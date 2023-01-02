package compiler.intermediate

import compiler.analysis.VariablePropertiesAnalyzer
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Type
import compiler.ast.Variable
import compiler.diagnostics.Diagnostic
import compiler.diagnostics.Diagnostics
import compiler.intermediate.generators.DISPLAY_LABEL_IN_MEMORY
import compiler.intermediate.generators.DefaultFunctionDetailsGenerator
import compiler.intermediate.generators.ForeignFunctionDetailsGenerator
import compiler.intermediate.generators.FunctionDetailsGenerator
import compiler.intermediate.generators.VariableLocationType
import compiler.utils.KeyRefMap
import compiler.utils.Ref
import compiler.utils.RefMap
import compiler.utils.RefSet
import compiler.utils.mutableKeyRefMapOf
import compiler.utils.refSetOf

object FunctionDependenciesAnalyzer {
    fun createUniqueIdentifiers(program: Program, allowInconsistentNamingErrors: Boolean): KeyRefMap<Function, UniqueIdentifier> {
        val uniqueIdentifiers = mutableKeyRefMapOf<Function, UniqueIdentifier>()
        val identifierFactory = UniqueIdentifierFactory()
        fun nameFunction(function: Function, pathSoFar: String?): String {
            try {
                uniqueIdentifiers[Ref(function)] = identifierFactory.build(pathSoFar, function.name)
            } catch (error: InconsistentFunctionNamingConvention) {
                // Such errors should occur only as a consequence of skipped NameConflict error in name resolution.
                // Hence, if `allowInconsistentNamingErrors`, which is only set when there were already some errors
                // in diagnostics, we can safely ignore such errors, cause the compilation would be failed later anyway.
                if (!allowInconsistentNamingErrors)
                    throw error
                uniqueIdentifiers[Ref(function)] = UniqueIdentifier("\$INVALID")
            }
            return uniqueIdentifiers[Ref(function)]!!.value
        }

        fun analyze(node: AstNode, pathSoFar: String? = null) {
            when (node) {
                is Program.Global.FunctionDefinition -> { analyze(node.function, pathSoFar) }
                is Statement.FunctionDefinition -> { analyze(node.function, pathSoFar) }
                is Function -> if (node.implementation is Function.Implementation.Local) {
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
        return uniqueIdentifiers
    }

    fun extractMainFunction(program: Program, diagnostics: Diagnostics): Function? {
        val mainFunction = (
            program.globals.find {
                it is Program.Global.FunctionDefinition && it.function.name == MAIN_FUNCTION_IDENTIFIER
            } as Program.Global.FunctionDefinition?
            )?.function
        if (mainFunction == null) {
            diagnostics.report(Diagnostic.MainFunctionNotFound)
        }
        return mainFunction
    }

    fun createFunctionDetailsGenerators(
        program: Program,
        variableProperties: KeyRefMap<AstNode, VariablePropertiesAnalyzer.VariableProperties>,
        functionReturnedValueVariables: KeyRefMap<Function, Variable>,
        allowInconsistentNamingErrors: Boolean = false
    ): KeyRefMap<Function, FunctionDetailsGenerator> {
        val result = mutableKeyRefMapOf<Function, FunctionDetailsGenerator>()
        val functionIdentifiers = createUniqueIdentifiers(program, allowInconsistentNamingErrors)

        fun createDetailsGenerator(function: Function, depth: ULong) {
            result[Ref(function)] = when (function.implementation) {

                is Function.Implementation.Foreign -> {
                    ForeignFunctionDetailsGenerator(
                        IFTNode.MemoryLabel(function.implementation.foreignName),
                        function.returnType !is Type.Unit
                    )
                }

                is Function.Implementation.Local -> {
                    val variablesLocationTypes = mutableKeyRefMapOf<NamedNode, VariableLocationType>()
                    variableProperties
                        .filter { (_, properties) -> properties.owner === function }
                        .forEach { (variable, properties) ->
                            variablesLocationTypes[Ref(variable.value as NamedNode)] =
                                if (properties.accessedIn.any { it != Ref(function) } || properties.writtenIn.any { it != Ref(function) })
                                    VariableLocationType.MEMORY
                                else VariableLocationType.REGISTER
                        }

                    DefaultFunctionDetailsGenerator(
                        function.parameters,
                        functionReturnedValueVariables[Ref(function)],
                        IFTNode.MemoryLabel(functionIdentifiers[Ref(function)]!!.value),
                        depth,
                        variablesLocationTypes,
                        IFTNode.MemoryLabel(DISPLAY_LABEL_IN_MEMORY)
                    )
                }
            }
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

    fun createCallGraph(ast: Program, nameResolution: RefMap<AstNode, NamedNode>): KeyRefMap<Function, RefSet<Function>> {

        val functionCalls = mutableKeyRefMapOf<Function, RefSet<Function>>()

        fun getCalledFunctions(global: Program.Global): RefSet<Function> {
            fun getCalledFunctions(expression: Expression?): RefSet<Function> = when (expression) {
                is Expression.BooleanLiteral -> refSetOf()
                is Expression.NumberLiteral -> refSetOf()
                is Expression.UnitLiteral -> refSetOf()
                is Expression.Variable -> refSetOf()
                null -> refSetOf()

                is Expression.FunctionCall -> refSetOf(nameResolution[Ref(expression)]!!.value as Function) +
                    expression.arguments.map { getCalledFunctions(it.value) }.fold(emptySet(), RefSet<Function>::plus)

                is Expression.UnaryOperation -> getCalledFunctions(expression.operand)

                is Expression.BinaryOperation -> getCalledFunctions(expression.leftOperand) +
                    getCalledFunctions(expression.rightOperand)

                is Expression.Conditional -> getCalledFunctions(expression.condition) +
                    getCalledFunctions(expression.resultWhenTrue) +
                    getCalledFunctions(expression.resultWhenFalse)
            }

            fun getCalledFunctions(statement: Statement): RefSet<Function> {
                fun getCalledFunctions(list: List<Statement>?): RefSet<Function> =
                    if (list === null) refSetOf() else list.map { getCalledFunctions(it) }.fold(emptySet(), RefSet<Function>::plus)

                return when (statement) {
                    is Statement.LoopBreak -> refSetOf()
                    is Statement.LoopContinuation -> refSetOf()

                    is Statement.FunctionDefinition -> {
                        functionCalls[Ref(statement.function)] = getCalledFunctions(statement.function.body)
                        return statement.function.parameters.map { getCalledFunctions(it.defaultValue) }.fold(emptySet(), RefSet<Function>::plus)
                    }

                    is Statement.Evaluation -> getCalledFunctions(statement.expression)

                    is Statement.VariableDefinition -> getCalledFunctions(statement.variable.value)

                    is Statement.Assignment -> getCalledFunctions(statement.value)

                    is Statement.Block -> getCalledFunctions(statement.block)

                    is Statement.FunctionReturn -> getCalledFunctions(statement.value)

                    is Statement.Conditional -> getCalledFunctions(statement.condition) +
                        getCalledFunctions(statement.actionWhenTrue) +
                        getCalledFunctions(statement.actionWhenFalse)

                    is Statement.Loop -> getCalledFunctions(statement.condition) +
                        getCalledFunctions(statement.action)
                }
            }

            return when (global) {
                is Program.Global.VariableDefinition -> refSetOf()

                is Program.Global.FunctionDefinition -> {
                    functionCalls[Ref(global.function)] = global.function.body.map { getCalledFunctions(it) }.fold(emptySet(), RefSet<Function>::plus)
                    return refSetOf()
                }
            }
        }

        ast.globals.forEach { getCalledFunctions(it) }

        val allFunctions = functionCalls.keys
        var previousPartialTransitiveFunctionCalls: KeyRefMap<Function, RefSet<Function>>
        var nextPartialTransitiveFunctionCalls = functionCalls

        fun getAllCallsOfChildren(calls: KeyRefMap<Function, RefSet<Function>>, function: Function): RefSet<Function> =
            calls[Ref(function)]!!.map { calls[it]!! }.fold(emptySet(), RefSet<Function>::plus)

        repeat(allFunctions.size) {
            previousPartialTransitiveFunctionCalls = nextPartialTransitiveFunctionCalls
            nextPartialTransitiveFunctionCalls = mutableKeyRefMapOf()
            allFunctions.forEach {
                nextPartialTransitiveFunctionCalls[it] =
                    previousPartialTransitiveFunctionCalls[it]!! +
                    getAllCallsOfChildren(previousPartialTransitiveFunctionCalls, it.value)
            }
        }

        return nextPartialTransitiveFunctionCalls
    }
}
