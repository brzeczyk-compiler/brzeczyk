package compiler.intermediate

import compiler.analysis.BuiltinFunctions
import compiler.analysis.VariablePropertiesAnalyzer
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Variable
import compiler.intermediate.generators.DISPLAY_LABEL_IN_MEMORY
import compiler.intermediate.generators.DefaultFunctionDetailsGenerator
import compiler.intermediate.generators.FunctionDetailsGenerator
import compiler.intermediate.generators.VariableLocationType
import compiler.utils.ReferenceHashMap
import compiler.utils.ReferenceMap
import compiler.utils.ReferenceSet
import compiler.utils.combineReferenceSets
import compiler.utils.referenceHashMapOf
import compiler.utils.referenceHashSetOf
import compiler.utils.referenceKeys

object FunctionDependenciesAnalyzer {
    fun createUniqueIdentifiers(program: Program, allowInconsistentNamingErrors: Boolean): ReferenceMap<Function, UniqueIdentifier> {
        val uniqueIdentifiers = referenceHashMapOf<Function, UniqueIdentifier>()
        val identifierFactory = UniqueIdentifierFactory()
        fun nameFunction(function: Function, pathSoFar: String?): String {
            try {
                uniqueIdentifiers[function] = identifierFactory.build(pathSoFar, function.name)
            } catch (error: InconsistentFunctionNamingConvention) {
                // Such errors should occur only as a consequence of skipped NameConflict error in name resolution.
                // Hence, if `allowInconsistentNamingErrors`, which is only set when there were already some errors
                // in diagnostics, we can safely ignore such errors, cause the compilation would be failed later anyway.
                if (!allowInconsistentNamingErrors)
                    throw error
                uniqueIdentifiers[function] = UniqueIdentifier("\$INVALID")
            }
            return uniqueIdentifiers[function]!!.value
        }

        fun analyze(node: Any, pathSoFar: String? = null) {
            when (node) {
                is Program.Global.FunctionDefinition -> { analyze(node.function, pathSoFar) }
                is Statement.FunctionDefinition -> { analyze(node.function, pathSoFar) }
                is Function -> {
                    val newPrefix = nameFunction(node, pathSoFar)
                    var blockNumber = 0
                    fun handleNestedBlock(statements: List<Statement>) = statements.forEach { analyze(it, newPrefix + "@block" + blockNumber++) }
                    for (statement in node.body) {
                        when (statement) {
                            is Statement.Block -> {
                                handleNestedBlock(statement.block)
                            }
                            is Statement.Conditional -> {
                                handleNestedBlock(statement.actionWhenTrue)
                                handleNestedBlock(statement.actionWhenFalse ?: listOf())
                            }
                            is Statement.Loop -> {
                                handleNestedBlock(statement.action)
                            }
                            else -> analyze(statement, newPrefix)
                        }
                    }
                }
                else -> {}
            }
        }
        program.globals.forEach { analyze(it) }
        BuiltinFunctions.getUsedBuiltinFunctions(program).forEach { nameFunction(it, null) }
        return uniqueIdentifiers
    }

    fun createFunctionDetailsGenerators(
        program: Program,
        variableProperties: ReferenceMap<Any, VariablePropertiesAnalyzer.VariableProperties>,
        functionReturnedValueVariables: ReferenceMap<Function, Variable>,
        allowInconsistentNamingErrors: Boolean = false
    ): ReferenceMap<Function, FunctionDetailsGenerator> {
        val result = referenceHashMapOf<Function, FunctionDetailsGenerator>()
        val functionIdentifiers = createUniqueIdentifiers(program, allowInconsistentNamingErrors)

        fun createDetailsGenerator(function: Function, depth: ULong) {
            val variablesLocationTypes = referenceHashMapOf<NamedNode, VariableLocationType>()
            variableProperties
                .filter { (_, properties) -> properties.owner === function }
                .forEach { (variable, properties) ->
                    variablesLocationTypes[variable as NamedNode] =
                        if (properties.accessedIn.any { it != function } || properties.writtenIn.any { it != function })
                            VariableLocationType.MEMORY
                        else VariableLocationType.REGISTER
                }

            result[function] = DefaultFunctionDetailsGenerator(
                function.parameters,
                functionReturnedValueVariables[function],
                IFTNode.MemoryLabel(functionIdentifiers[function]!!.value),
                depth,
                variablesLocationTypes,
                IFTNode.MemoryLabel(DISPLAY_LABEL_IN_MEMORY)
            )
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
        BuiltinFunctions.getUsedBuiltinFunctions(program).forEach { createDetailsGenerator(it, 0u) }

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
        BuiltinFunctions.getUsedBuiltinFunctions(ast).forEach { functionCalls[it] = referenceHashSetOf() }

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
