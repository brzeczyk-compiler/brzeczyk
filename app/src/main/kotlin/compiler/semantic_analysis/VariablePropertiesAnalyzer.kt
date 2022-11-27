package compiler.semantic_analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Program.Global
import compiler.ast.Statement
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostic.VariablePropertiesError.AssignmentToFunctionParameter
import compiler.common.diagnostics.Diagnostics
import compiler.common.reference_collections.MutableReferenceMap
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.ReferenceSet
import compiler.common.reference_collections.referenceEntries

object VariablePropertiesAnalyzer {
    data class VariableProperties(
        var owner: Function? = null,
        val accessedIn: MutableSet<Function> = mutableSetOf(),
        val writtenIn: MutableSet<Function> = mutableSetOf()
    )

    fun calculateVariableProperties(
        ast: Program,
        nameResolution: ReferenceMap<Any, NamedNode>,
        defaultParameterMapping: ReferenceMap<Function.Parameter, Variable>,
        accessedDefaultValues: ReferenceMap<Expression.FunctionCall, ReferenceSet<Function.Parameter>>,
        diagnostics: Diagnostics,
    ): ReferenceMap<Any, VariableProperties> {
        val variableProperties: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        val functionCallsOwnership: MutableReferenceMap<Expression.FunctionCall, Function> = ReferenceHashMap()

        // Any = Expression | Statement
        fun analyzeVariables(node: Any, currentFunction: Function?) {
            when (node) {
                is Statement.Evaluation -> analyzeVariables(node.expression, currentFunction)
                is Statement.VariableDefinition -> {
                    variableProperties[node.variable] = VariableProperties(currentFunction)
                    analyzeVariables(node.variable, currentFunction)
                }
                is Global.VariableDefinition -> {
                    variableProperties[node.variable] = VariableProperties(currentFunction)
                    analyzeVariables(node.variable, currentFunction)
                }
                is Statement.FunctionDefinition -> analyzeVariables(node.function, currentFunction)
                is Global.FunctionDefinition -> analyzeVariables(node.function, currentFunction)
                is Statement.Assignment -> {
                    val resolvedVariable: NamedNode = nameResolution[node]!!
                    variableProperties[resolvedVariable]!!.writtenIn.add(currentFunction!!)
                    if (resolvedVariable is Function.Parameter) {
                        diagnostics.report(
                            AssignmentToFunctionParameter(
                                resolvedVariable,
                                variableProperties[resolvedVariable]!!.owner!!,
                                currentFunction
                            )
                        )
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
                    variableProperties[resolvedVariable]!!.accessedIn.add(currentFunction!!)
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
                        variableProperties[it] = VariableProperties(node)
                        if (it.defaultValue != null) {
                            // scope of the inner function has not begun yet
                            analyzeVariables(it.defaultValue, currentFunction)
                            // TODO: uncomment after updating tests
                            // variableProperties[defaultParameterMapping[it]!!] = VariableProperties(currentFunction)
                        }
                    }
                    node.body.forEach { analyzeVariables(it, node) }
                }
                is Variable -> {
                    node.value?.let { analyzeVariables(it, currentFunction) }
                }
            }
        }
        ast.globals.forEach { analyzeVariables(it, null) }

        // update properties of default parameters dummy variables
        defaultParameterMapping.referenceEntries.forEach { paramToVariable ->
            run {
                val accessedIn = accessedDefaultValues.referenceEntries.filter { paramToVariable.key in it.value }.map { functionCallsOwnership[it.key]!! }.toMutableSet()
                val owner = variableProperties[paramToVariable.value]!!.owner
                val writtenIn = if (owner != null) mutableSetOf(owner) else mutableSetOf()
                variableProperties[paramToVariable.value] = VariableProperties(owner, accessedIn, writtenIn)
            }
        }

        return variableProperties
    }
}
