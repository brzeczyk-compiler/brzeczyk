package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.ReferenceSet
import compiler.common.reference_collections.combineReferenceSets
import compiler.common.reference_collections.referenceHashMapOf
import compiler.common.reference_collections.referenceHashSetOf
import compiler.common.reference_collections.referenceKeys
import compiler.semantic_analysis.VariablePropertiesAnalyzer

object FunctionDependenciesAnalyzer {
    fun createUniqueIdentifiers(program: Program): ReferenceMap<Function, UniqueIdentifier> {
        val uniqueIdentifiers = referenceHashMapOf<Function, UniqueIdentifier>()
        val identifierFactory = UniqueIdentifierFactory()
        fun nameFunction(function: Function, pathSoFar: String?): String {
            uniqueIdentifiers[function] = identifierFactory.build(pathSoFar, function.name)
            return uniqueIdentifiers[function]!!.value
        }

        fun analyze(node: Any, pathSoFar: String? = null) {
            when (node) {
                is Program.Global.FunctionDefinition -> { analyze(node.function, pathSoFar) }
                is Statement.FunctionDefinition -> { analyze(node.function, pathSoFar) }
                is Function -> {
                    val newPrefix = nameFunction(node, pathSoFar)
                    var blockNumber = -1
                    for (statement in node.body) {
                        when (statement) {
                            is Statement.Block -> {
                                blockNumber++
                                statement.block.forEach { analyze(it, newPrefix + blockNumber) }
                                analyze(statement, newPrefix + blockNumber)
                            }
                            is Statement.Conditional -> {
                                blockNumber++
                                statement.actionWhenTrue.forEach { analyze(it, newPrefix + blockNumber) }
                                blockNumber++
                                statement.actionWhenFalse?.forEach { analyze(it, newPrefix + blockNumber) }
                            }
                            is Statement.Loop -> {
                                blockNumber++
                                statement.action.forEach { analyze(it, newPrefix + blockNumber) }
                                analyze(statement, newPrefix + blockNumber)
                            }
                            else -> analyze(statement, newPrefix)
                        }
                    }
                }
                else -> {}
            }
        }
        program.globals.forEach { analyze(it) }
        return uniqueIdentifiers
    }

    fun createFunctionDetailsGenerators(
        program: Program,
        variableProperties: ReferenceMap<Any, VariablePropertiesAnalyzer.VariableProperties>
    ): ReferenceMap<Function, DefaultFunctionDetailsGenerator> {

        val result = referenceHashMapOf<Function, DefaultFunctionDetailsGenerator>()

        fun createDetailsGenerator(function: Function, depth: ULong) {
            val variables = referenceHashMapOf<NamedNode, Boolean>()
            variableProperties
                .filter { (_, properties) -> properties.owner === function }
                .forEach { (variable, properties) ->
                    variables[variable as NamedNode] = (properties.accessedIn.any { it != function } || properties.writtenIn.any { it != function })
                }

            result[function] = TODO() // FunctionDetailsGenerator(depth, variables, function.parameters)
        }

        fun processFunction(function: Function, depth: ULong) {
            createDetailsGenerator(function, depth)

            fun processBlock(block: StatementBlock) {
                for (statement in block) {
                    when (statement) {
                        is Statement.FunctionDefinition -> processFunction(statement.function, depth + 1u)
                        is Statement.Block -> processBlock(statement.block)
                        is Statement.Conditional -> {
                            processBlock(statement.actionWhenTrue)
                            statement.actionWhenFalse?.let { processBlock(it) }
                        }
                        is Statement.Loop -> processBlock(statement.action)
                        else -> {}
                    }
                }
            }

            processBlock(function.body)
        }

        program.globals.forEach { if (it is Program.Global.FunctionDefinition) processFunction(it.function, 0u) }

        return result
    }

    fun createCallGraph(ast: Program, nameResolution: ReferenceMap<Any, NamedNode>): ReferenceMap<Function, ReferenceSet<Function>> {

        val functionCalls = referenceHashMapOf<Function, ReferenceSet<Function>>()

        fun getCalledFunctions(global: Program.Global): ReferenceSet<Function> {
            fun getCalledFunctions(expression: Expression?): ReferenceSet<Function> = when (expression) {
                is Expression.BooleanLiteral -> referenceHashSetOf()
                is Expression.NumberLiteral -> referenceHashSetOf()
                is Expression.UnitLiteral -> referenceHashSetOf()
                is Expression.Variable -> referenceHashSetOf()
                null -> referenceHashSetOf()

                is Expression.FunctionCall -> combineReferenceSets(
                    referenceHashSetOf(nameResolution[expression] as Function),
                    combineReferenceSets(expression.arguments.map { getCalledFunctions(it.value) }),
                )

                is Expression.UnaryOperation -> getCalledFunctions(expression.operand)

                is Expression.BinaryOperation -> combineReferenceSets(
                    getCalledFunctions(expression.leftOperand),
                    getCalledFunctions(expression.rightOperand),
                )

                is Expression.Conditional -> combineReferenceSets(
                    getCalledFunctions(expression.condition),
                    getCalledFunctions(expression.resultWhenTrue),
                    getCalledFunctions(expression.resultWhenFalse),
                )
            }

            fun getCalledFunctions(statement: Statement): ReferenceSet<Function> {
                fun getCalledFunctions(list: List<Statement>?): ReferenceSet<Function> =
                    if (list === null) referenceHashSetOf() else combineReferenceSets(list.map { getCalledFunctions(it) })

                return when (statement) {
                    is Statement.LoopBreak -> referenceHashSetOf()
                    is Statement.LoopContinuation -> referenceHashSetOf()

                    is Statement.FunctionDefinition -> {
                        functionCalls[statement.function] = getCalledFunctions(statement.function.body)
                        return combineReferenceSets(statement.function.parameters.map { getCalledFunctions(it.defaultValue) })
                    }

                    is Statement.Evaluation -> getCalledFunctions(statement.expression)

                    is Statement.VariableDefinition -> getCalledFunctions(statement.variable.value)

                    is Statement.Assignment -> getCalledFunctions(statement.value)

                    is Statement.Block -> getCalledFunctions(statement.block)

                    is Statement.FunctionReturn -> getCalledFunctions(statement.value)

                    is Statement.Conditional -> combineReferenceSets(
                        getCalledFunctions(statement.condition),
                        getCalledFunctions(statement.actionWhenTrue),
                        getCalledFunctions(statement.actionWhenFalse),
                    )

                    is Statement.Loop -> combineReferenceSets(
                        getCalledFunctions(statement.condition),
                        getCalledFunctions(statement.action),
                    )
                }
            }

            return when (global) {
                is Program.Global.VariableDefinition -> referenceHashSetOf()

                is Program.Global.FunctionDefinition -> {
                    functionCalls[global.function] = combineReferenceSets(global.function.body.map { getCalledFunctions(it) })
                    return referenceHashSetOf()
                }
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
            nextPartialTransitiveFunctionCalls = referenceHashMapOf()
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
