package compiler.analysis

import compiler.Compiler.CompilationFailed
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Program.Global
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.ast.VariableOwner
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.VariablePropertiesError.AssignmentToFunctionParameter
import compiler.diagnostics.Diagnostics
import compiler.utils.KeyRefMap
import compiler.utils.MutableKeyRefMap
import compiler.utils.MutableRefMap
import compiler.utils.MutableRefSet
import compiler.utils.Ref
import compiler.utils.RefMap
import compiler.utils.RefSet
import compiler.utils.mutableKeyRefMapOf
import compiler.utils.mutableRefMapOf
import compiler.utils.mutableRefSetOf
import compiler.utils.refSetOf

object VariablePropertiesAnalyzer {

    object GlobalContext : VariableOwner

    data class VariableProperties(
        var owner: VariableOwner = GlobalContext,
        val accessedIn: RefSet<Function> = refSetOf(),
        val writtenIn: RefSet<Function> = refSetOf(),
    )

    data class MutableVariableProperties(
        var owner: VariableOwner = GlobalContext,
        val accessedIn: MutableRefSet<Function> = mutableRefSetOf(),
        val writtenIn: MutableRefSet<Function> = mutableRefSetOf(),
    )

    class AnalysisFailed : CompilationFailed()

    fun fixVariableProperties(mutable: MutableVariableProperties): VariableProperties = VariableProperties(mutable.owner, mutable.accessedIn, mutable.writtenIn)

    fun calculateVariableProperties(
        ast: Program,
        nameResolution: RefMap<AstNode, NamedNode>,
        defaultParameterMapping: KeyRefMap<Function.Parameter, Variable>,
        functionReturnedValueVariables: KeyRefMap<Function, Variable>,
        accessedDefaultValues: KeyRefMap<Expression.FunctionCall, RefSet<Function.Parameter>>,
        diagnostics: Diagnostics,
    ): KeyRefMap<AstNode, VariableProperties> {
        val mutableVariableProperties: MutableKeyRefMap<AstNode, MutableVariableProperties> = mutableKeyRefMapOf()
        val functionCallsOwnership: MutableRefMap<Expression.FunctionCall, Function> = mutableRefMapOf()
        var failed = false

        // AstNode = Expression | Statement
        fun analyzeVariables(node: AstNode, currentFunction: Function?) {
            when (node) {
                is Statement.Evaluation -> analyzeVariables(node.expression, currentFunction)
                is Statement.VariableDefinition -> {
                    mutableVariableProperties[Ref(node.variable)] = MutableVariableProperties(currentFunction ?: GlobalContext)
                    analyzeVariables(node.variable, currentFunction)
                }
                is Global.VariableDefinition -> {
                    mutableVariableProperties[Ref(node.variable)] = MutableVariableProperties(currentFunction ?: GlobalContext)
                    analyzeVariables(node.variable, currentFunction)
                }
                is Statement.FunctionDefinition -> analyzeVariables(node.function, currentFunction)
                is Global.FunctionDefinition -> analyzeVariables(node.function, currentFunction)
                is Statement.Assignment -> {
                    val resolvedVariable: NamedNode = nameResolution[Ref(node)]!!.value
                    mutableVariableProperties[Ref(resolvedVariable)]!!.writtenIn.add(Ref(currentFunction!!))
                    if (resolvedVariable is Function.Parameter) {
                        diagnostics.report( // FIXME: this is also reported by the type checker
                            AssignmentToFunctionParameter(
                                resolvedVariable,
                                mutableVariableProperties[Ref(resolvedVariable)]!!.owner as Function,
                                currentFunction
                            )
                        )
                        failed = true
                    }
                    analyzeVariables(node.value, currentFunction)
                }
                is Statement.Block -> node.block.forEach { analyzeVariables(it, currentFunction) }
                is Statement.Conditional -> (
                    sequenceOf(node.condition) +
                        node.actionWhenTrue.asSequence() + (node.actionWhenFalse?.asSequence() ?: emptySequence())
                    ).forEach { analyzeVariables(it, currentFunction) }
                is Statement.Loop -> (sequenceOf(node.condition) + node.action.asSequence())
                    .forEach { analyzeVariables(it, currentFunction) }
                is Statement.FunctionReturn -> analyzeVariables(node.value, currentFunction)
                is Expression.Variable -> {
                    val resolvedVariable: NamedNode = nameResolution[Ref(node)]!!.value
                    mutableVariableProperties[Ref(resolvedVariable)]!!.accessedIn.add(Ref(currentFunction!!))
                }
                is Expression.FunctionCall -> {
                    node.arguments.forEach { analyzeVariables(it, currentFunction) }
                    functionCallsOwnership[Ref(node)] = Ref(currentFunction!!)
                }
                is Expression.FunctionCall.Argument -> analyzeVariables(node.value, currentFunction)
                is Expression.UnaryOperation -> analyzeVariables(node.operand, currentFunction)
                is Expression.BinaryOperation -> sequenceOf(node.leftOperand, node.rightOperand)
                    .forEach { analyzeVariables(it, currentFunction) }
                is Expression.Conditional -> sequenceOf(node.condition, node.resultWhenTrue, node.resultWhenFalse)
                    .forEach { analyzeVariables(it, currentFunction) }
                is Function -> {
                    node.parameters.forEach {
                        mutableVariableProperties[Ref(it)] = MutableVariableProperties(node)
                        if (it.defaultValue != null) {
                            // scope of the inner function has not begun yet
                            analyzeVariables(it.defaultValue, currentFunction)
                            mutableVariableProperties[Ref(defaultParameterMapping[Ref(it)]!!)] = MutableVariableProperties(currentFunction ?: GlobalContext)
                        }
                    }
                    node.body.forEach { analyzeVariables(it, node) }
                    if (node.implementation is Function.Implementation.Local && node.returnType != Type.Unit) {
                        // Accessed set consists of only the owner function, cause the variable's value is moved to
                        // appropriate Register in the ControlFlowGraph of epilogue of this function, and the Variable
                        // is never accessed anymore (including both outer & inner functions).
                        mutableVariableProperties[Ref(functionReturnedValueVariables[Ref(node)]!!)] = MutableVariableProperties(
                            node,
                            mutableRefSetOf(node),
                            mutableRefSetOf(node)
                        )
                    }
                }
                is Variable -> node.value?.let { analyzeVariables(it, currentFunction) }
                else -> {}
            }
        }

        ast.globals.forEach { analyzeVariables(it, null) }
        val fixedVariableProperties = mutableVariableProperties.map { it.key to fixVariableProperties(it.value) }.toMutableList()

        val defaultParametersDummyVariablesProperties = defaultParameterMapping.map { paramToVariable ->
            val accessedIn = accessedDefaultValues.filter { paramToVariable.key in it.value }.map { functionCallsOwnership[it.key]!! }.toSet()
            val owner = mutableVariableProperties[Ref(paramToVariable.value)]!!.owner
            val writtenIn = if (owner != GlobalContext) refSetOf(owner as Function) else refSetOf()
            Ref(paramToVariable.value) to VariableProperties(owner, accessedIn, writtenIn)
        }.toList()

        fixedVariableProperties.addAll(defaultParametersDummyVariablesProperties)

        if (failed)
            throw AnalysisFailed()

        return fixedVariableProperties.toMap()
    }
}
