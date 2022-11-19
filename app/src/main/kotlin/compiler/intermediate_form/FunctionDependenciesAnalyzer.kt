package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.ReferenceSet
import compiler.common.reference_collections.combineReferenceSets
import compiler.common.reference_collections.referenceKeys
import compiler.common.reference_collections.referenceSetOf

object FunctionDependenciesAnalyzer {
    enum class VariableAccessMode {
        READ_ONLY,
        READ_WRITE
    }

    fun variablesUsedByFunctions(ast: Program): ReferenceMap<Function, ReferenceMap<Variable, VariableAccessMode>> {
        return TODO()
    }

    fun createCallGraph(ast: Program, nameResolution: ReferenceMap<Any, NamedNode>): ReferenceMap<Function, ReferenceSet<Function>> {

        val functionCalls: ReferenceHashMap<Function, ReferenceSet<Function>> = ReferenceHashMap()

        fun getCalledFunctions(node: Any?): ReferenceSet<Function> {

            fun getCalledOfStatementBlock(statementBlock: StatementBlock?): ReferenceSet<Function> =
                if (statementBlock === null) referenceSetOf() else combineReferenceSets(statementBlock.map { getCalledFunctions(it) })

            return when (node) {

                is Program.Global.FunctionDefinition -> {
                    functionCalls[node.function] = getCalledOfStatementBlock(node.function.body)
                    return referenceSetOf()
                }

                // Expressions

                is Expression.FunctionCall -> combineReferenceSets(
                    referenceSetOf(nameResolution[node] as Function),
                    combineReferenceSets(node.arguments.map { getCalledFunctions(it.value) }),
                )

                is Expression.UnaryOperation -> getCalledFunctions(node.operand)

                is Expression.BinaryOperation -> combineReferenceSets(
                    getCalledFunctions(node.leftOperand),
                    getCalledFunctions(node.rightOperand),
                )

                is Expression.Conditional -> combineReferenceSets(
                    getCalledFunctions(node.condition),
                    getCalledFunctions(node.resultWhenTrue),
                    getCalledFunctions(node.resultWhenFalse),
                )

                // Statements

                is Statement.Evaluation -> getCalledFunctions(node.expression)

                is Statement.VariableDefinition -> getCalledFunctions(node.variable.value)

                is Statement.FunctionDefinition -> {
                    functionCalls[node.function] = getCalledOfStatementBlock(node.function.body)
                    return combineReferenceSets(node.function.parameters.map { it.defaultValue }.map { getCalledFunctions(it) })
                }

                is Statement.Assignment -> getCalledFunctions(node.value)

                is Statement.Block -> getCalledOfStatementBlock(node.block)

                is Statement.Conditional -> combineReferenceSets(
                    getCalledFunctions(node.condition),
                    getCalledOfStatementBlock(node.actionWhenTrue),
                    getCalledOfStatementBlock(node.actionWhenFalse),
                )

                is Statement.Loop -> combineReferenceSets(
                    getCalledFunctions(node.condition),
                    getCalledOfStatementBlock(node.action),
                )

                is Statement.FunctionReturn -> getCalledFunctions(node.value)

                else -> referenceSetOf()
            }
        }

        ast.globals.forEach { getCalledFunctions(it) }

        val allFunctions = functionCalls.referenceKeys
        var previousPartialTransitiveFunctionCalls: ReferenceHashMap<Function, ReferenceSet<Function>>
        var nextPartialTransitiveFunctionCalls = functionCalls

        fun getAllCallsOfChildren(calls: ReferenceHashMap<Function, ReferenceSet<Function>>, function: Function): ReferenceSet<Function> =
            combineReferenceSets(calls[function]!!.map { calls[it]!! })

        repeat(allFunctions.size) {
            previousPartialTransitiveFunctionCalls = nextPartialTransitiveFunctionCalls
            nextPartialTransitiveFunctionCalls = ReferenceHashMap()
            allFunctions.forEach {
                nextPartialTransitiveFunctionCalls[it] = combineReferenceSets(
                    previousPartialTransitiveFunctionCalls[it]!!,
                    getAllCallsOfChildren(previousPartialTransitiveFunctionCalls, it),
                )
            }
        }

        return nextPartialTransitiveFunctionCalls
    }
}
