package compiler.analysis

import compiler.Compiler.CompilationFailed
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
import compiler.utils.MutableReferenceMap
import compiler.utils.MutableReferenceSet
import compiler.utils.ReferenceMap
import compiler.utils.ReferenceSet
import compiler.utils.referenceEntries
import compiler.utils.referenceHashMapOf
import compiler.utils.referenceHashSetOf

object VariablePropertiesAnalyzer {

    object GlobalContext : VariableOwner

    data class VariableProperties(
        var owner: VariableOwner = GlobalContext,
        val accessedIn: ReferenceSet<Function> = referenceHashSetOf(),
        val writtenIn: ReferenceSet<Function> = referenceHashSetOf(),
    )

    data class MutableVariableProperties(
        var owner: VariableOwner = GlobalContext,
        val accessedIn: MutableReferenceSet<Function> = referenceHashSetOf(),
        val writtenIn: MutableReferenceSet<Function> = referenceHashSetOf(),
    )

    class AnalysisFailed : CompilationFailed()

    fun fixVariableProperties(mutable: MutableVariableProperties): VariableProperties = VariableProperties(mutable.owner, mutable.accessedIn, mutable.writtenIn)

    fun calculateVariableProperties(
        ast: Program,
        nameResolution: ReferenceMap<Any, NamedNode>,
        defaultParameterMapping: ReferenceMap<Function.Parameter, Variable>,
        functionReturnedValueVariables: ReferenceMap<Function, Variable>,
        accessedDefaultValues: ReferenceMap<Expression.FunctionCall, ReferenceSet<Function.Parameter>>,
        diagnostics: Diagnostics,
    ): ReferenceMap<Any, VariableProperties> {
        val mutableVariableProperties: MutableReferenceMap<Any, MutableVariableProperties> = referenceHashMapOf()
        val functionCallsOwnership: MutableReferenceMap<Expression.FunctionCall, Function> = referenceHashMapOf()
        var failed = false

        // Any = Expression | Statement
        fun analyzeVariables(node: Any, currentFunction: Function?) {
            when (node) {
                is Statement.Evaluation -> analyzeVariables(node.expression, currentFunction)
                is Statement.VariableDefinition -> {
                    mutableVariableProperties[node.variable] = MutableVariableProperties(currentFunction ?: GlobalContext)
                    analyzeVariables(node.variable, currentFunction)
                }
                is Global.VariableDefinition -> {
                    mutableVariableProperties[node.variable] = MutableVariableProperties(currentFunction ?: GlobalContext)
                    analyzeVariables(node.variable, currentFunction)
                }
                is Statement.FunctionDefinition -> analyzeVariables(node.function, currentFunction)
                is Global.FunctionDefinition -> analyzeVariables(node.function, currentFunction)
                is Statement.Assignment -> {
                    val resolvedVariable: NamedNode = nameResolution[node]!!
                    mutableVariableProperties[resolvedVariable]!!.writtenIn.add(currentFunction!!)
                    if (resolvedVariable is Function.Parameter) {
                        diagnostics.report( // FIXME: this is also reported by the type checker
                            AssignmentToFunctionParameter(
                                resolvedVariable,
                                mutableVariableProperties[resolvedVariable]!!.owner as Function,
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
                    val resolvedVariable: NamedNode = nameResolution[node]!!
                    mutableVariableProperties[resolvedVariable]!!.accessedIn.add(currentFunction!!)
                }
                is Expression.FunctionCall -> {
                    node.arguments.forEach { analyzeVariables(it, currentFunction) }
                    functionCallsOwnership[node] = currentFunction!!
                }
                is Expression.FunctionCall.Argument -> analyzeVariables(node.value, currentFunction)
                is Expression.UnaryOperation -> analyzeVariables(node.operand, currentFunction)
                is Expression.BinaryOperation -> sequenceOf(node.leftOperand, node.rightOperand)
                    .forEach { analyzeVariables(it, currentFunction) }
                is Expression.Conditional -> sequenceOf(node.condition, node.resultWhenTrue, node.resultWhenFalse)
                    .forEach { analyzeVariables(it, currentFunction) }
                is Function -> {
                    node.parameters.forEach {
                        mutableVariableProperties[it] = MutableVariableProperties(node)
                        if (it.defaultValue != null) {
                            // scope of the inner function has not begun yet
                            analyzeVariables(it.defaultValue, currentFunction)
                            mutableVariableProperties[defaultParameterMapping[it]!!] = MutableVariableProperties(currentFunction ?: GlobalContext)
                        }
                    }
                    node.body.forEach { analyzeVariables(it, node) }
                    if (node.returnType != Type.Unit) {
                        // Accessed set consists of only the owner function, cause the variable's value is moved to
                        // appropriate Register in the ControlFlowGraph of epilogue of this function, and the Variable
                        // is never accessed anymore (including both outer & inner functions).
                        mutableVariableProperties[functionReturnedValueVariables[node]!!] = MutableVariableProperties(
                            node,
                            referenceHashSetOf(node),
                            referenceHashSetOf(node)
                        )
                    }
                }
                is Variable -> node.value?.let { analyzeVariables(it, currentFunction) }
            }
        }

        ast.globals.forEach { analyzeVariables(it, null) }
        val fixedVariableProperties = mutableVariableProperties.map { it.key to fixVariableProperties(it.value) }.toMutableList()

        val defaultParametersDummyVariablesProperties = defaultParameterMapping.referenceEntries.map { paramToVariable ->
            val accessedIn = referenceHashSetOf(accessedDefaultValues.referenceEntries.filter { paramToVariable.key in it.value }.map { functionCallsOwnership[it.key]!! }.toList())
            val owner = mutableVariableProperties[paramToVariable.value]!!.owner
            val writtenIn = if (owner != GlobalContext) referenceHashSetOf(owner as Function) else referenceHashSetOf()
            paramToVariable.value to VariableProperties(owner, accessedIn, writtenIn)
        }.toList()

        fixedVariableProperties.addAll(defaultParametersDummyVariablesProperties)

        if (failed)
            throw AnalysisFailed()

        return referenceHashMapOf(fixedVariableProperties)
    }
}
