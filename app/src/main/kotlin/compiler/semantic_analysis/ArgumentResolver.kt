package compiler.semantic_analysis

import compiler.Compiler.CompilationFailed
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.ReferenceSet
import compiler.common.reference_collections.referenceHashMapOf
import compiler.common.reference_collections.referenceHashSetOf

typealias ArgumentResolutionResult = ReferenceMap<Expression.FunctionCall.Argument, Function.Parameter>

class ArgumentResolver(private val nameResolution: ReferenceMap<Any, NamedNode>, private val diagnostics: Diagnostics) {
    data class ArgumentResolutionResult(
        val argumentsToParametersMap: ReferenceMap<Expression.FunctionCall.Argument, Function.Parameter>,
        val accessedDefaultValues: ReferenceMap<Expression.FunctionCall, ReferenceSet<Function.Parameter>>
    )

    class ResolutionFailed : CompilationFailed()

    private val argumentsToParametersMap = referenceHashMapOf<Expression.FunctionCall.Argument, Function.Parameter>()
    private val accessedDefaultValues = referenceHashMapOf<Expression.FunctionCall, ReferenceSet<Function.Parameter>>()
    private var failed = false

    companion object {
        fun calculateArgumentToParameterResolution(
            program: Program,
            nameResolution: ReferenceMap<Any, NamedNode>,
            diagnostics: Diagnostics
        ): ArgumentResolutionResult {
            val resolver = ArgumentResolver(nameResolution, diagnostics)

            resolver.resolveFunctionDefinitions(program)
            resolver.resolveFunctionCallsArguments(program)

            if (resolver.failed)
                throw ResolutionFailed()

            return ArgumentResolutionResult(resolver.argumentsToParametersMap, resolver.accessedDefaultValues)
        }
    }

    private fun report(diagnostic: Diagnostic.ArgumentResolutionError) {
        diagnostics.report(diagnostic)
        failed = true
    }

    private fun resolveFunctionCallArguments(functionCall: Expression.FunctionCall) {
        var foundNamedArgument = false
        for (argument in functionCall.arguments) {
            if (argument.name != null) {
                foundNamedArgument = true
            } else if (foundNamedArgument) {
                report(Diagnostic.ArgumentResolutionError.PositionalArgumentAfterNamed(functionCall))
                return
            }
        }

        val function: Function = nameResolution[functionCall] as Function
        val parameterNames = function.parameters.map { it.name }
        val isMatched = function.parameters.map { false }.toMutableList()

        if (functionCall.arguments.size > function.parameters.size) {
            report(Diagnostic.ArgumentResolutionError.TooManyArguments(functionCall))
            return
        }

        for ((index, argument) in functionCall.arguments.withIndex()) {
            if (argument.name == null) {
                argumentsToParametersMap[argument] = function.parameters[index]
                isMatched[index] = true
            } else {
                val parameterIndex = parameterNames.indexOf(argument.name)
                if (parameterIndex == -1) {
                    report(Diagnostic.ArgumentResolutionError.UnknownArgument(functionCall, argument.name))
                    return
                }
                if (isMatched[parameterIndex]) {
                    report(Diagnostic.ArgumentResolutionError.RepeatedArgument(functionCall, argument.name))
                    return
                }
                argumentsToParametersMap[argument] = function.parameters[parameterIndex]
                isMatched[parameterIndex] = true
            }
        }

        val parametersWithDefaultValue = mutableListOf<Function.Parameter>()

        for ((index, parameter) in function.parameters.withIndex()) {
            if (!isMatched[index]) {
                if (parameter.defaultValue == null) {
                    report(Diagnostic.ArgumentResolutionError.MissingArgument(functionCall, parameterNames[index]))
                } else {
                    parametersWithDefaultValue.add(parameter)
                }
            }
        }
        accessedDefaultValues[functionCall] = referenceHashSetOf(parametersWithDefaultValue)
    }

    private fun resolveFunctionCallsArguments(program: Program) {
        fun processExpression(expression: Expression) {
            when (expression) {
                is Expression.FunctionCall -> {
                    resolveFunctionCallArguments(expression)
                    for (argument in expression.arguments)
                        processExpression(argument.value)
                }
                is Expression.UnaryOperation -> {
                    processExpression(expression.operand)
                }
                is Expression.BinaryOperation -> {
                    processExpression(expression.leftOperand)
                    processExpression(expression.rightOperand)
                }
                is Expression.Conditional -> {
                    processExpression(expression.condition)
                    processExpression(expression.resultWhenTrue)
                    processExpression(expression.resultWhenFalse)
                }
                else -> {}
            }
        }

        fun processVariable(variable: Variable) {
            if (variable.value != null) {
                processExpression(variable.value)
            }
        }

        fun processFunction(function: Function) {
            fun processBlock(block: StatementBlock) {
                for (statement in block) {
                    when (statement) {
                        is Statement.Evaluation -> processExpression(statement.expression)
                        is Statement.VariableDefinition -> processVariable(statement.variable)
                        is Statement.FunctionDefinition -> processFunction(statement.function)
                        is Statement.Assignment -> processExpression(statement.value)
                        is Statement.Block -> processBlock(statement.block)
                        is Statement.Conditional -> {
                            processExpression(statement.condition)
                            processBlock(statement.actionWhenTrue)
                            statement.actionWhenFalse?.let { processBlock(it) }
                        }
                        is Statement.Loop -> {
                            processExpression(statement.condition)
                            processBlock(statement.action)
                        }
                        is Statement.FunctionReturn -> processExpression(statement.value)
                        else -> {}
                    }
                }
            }

            processBlock(function.body)
        }

        program.globals.forEach { if (it is Program.Global.FunctionDefinition) processFunction(it.function) }
    }

    private fun resolveFunctionDefinitions(program: Program) {
        fun defaultParametersBeforeNonDefault(function: Function): Boolean {
            var encounteredDefault = false
            for (parameter in function.parameters) {
                if (parameter.defaultValue == null) {
                    if (encounteredDefault)
                        return true
                } else {
                    encounteredDefault = true
                }
            }
            return false
        }

        fun processFunction(function: Function) {
            if (defaultParametersBeforeNonDefault(function)) {
                report(Diagnostic.ArgumentResolutionError.DefaultParametersNotLast(function))
            }

            fun processBlock(block: StatementBlock) {
                for (statement in block) {
                    when (statement) {
                        is Statement.FunctionDefinition -> processFunction(statement.function)
                        is Statement.Block -> processBlock(statement.block)
                        is Statement.Conditional -> {
                            processBlock(statement.actionWhenTrue)
                            statement.actionWhenFalse?.let { processBlock(it) }
                        }
                        is Statement.Loop -> {
                            processBlock(statement.action)
                        }
                        else -> {}
                    }
                }
            }

            processBlock(function.body)
        }

        program.globals.forEach { if (it is Program.Global.FunctionDefinition) processFunction(it.function) }
    }
}
