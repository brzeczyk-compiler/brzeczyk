package compiler.analysis

import compiler.Compiler.CompilationFailed
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Variable
import compiler.diagnostics.Diagnostic
import compiler.diagnostics.Diagnostics
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf
import compiler.utils.mutableRefMapOf

typealias ArgumentResolutionResult = Map<Ref<Expression.FunctionCall.Argument>, Ref<Function.Parameter>>

class ArgumentResolver(private val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>, private val diagnostics: Diagnostics) {
    data class ArgumentResolutionResult(
        val argumentsToParametersMap: Map<Ref<Expression.FunctionCall.Argument>, Ref<Function.Parameter>>,
        val accessedDefaultValues: Map<Ref<Expression.FunctionCall>, Set<Ref<Function.Parameter>>>
    )

    class ResolutionFailed : CompilationFailed()

    private val argumentsToParametersMap = mutableRefMapOf<Expression.FunctionCall.Argument, Function.Parameter>()
    private val accessedDefaultValues = mutableKeyRefMapOf<Expression.FunctionCall, Set<Ref<Function.Parameter>>>()
    private var failed = false

    companion object {
        fun calculateArgumentToParameterResolution(
            program: Program,
            nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>,
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

    private fun report(diagnostic: Diagnostic.ResolutionDiagnostic.ArgumentResolutionError) {
        diagnostics.report(diagnostic)
        failed = true
    }

    private fun resolveFunctionCallArguments(functionCall: Expression.FunctionCall) {
        var foundNamedArgument = false
        for (argument in functionCall.arguments) {
            if (argument.name != null) {
                foundNamedArgument = true
            } else if (foundNamedArgument) {
                report(Diagnostic.ResolutionDiagnostic.ArgumentResolutionError.PositionalArgumentAfterNamed(functionCall))
                return
            }
        }

        val function: Function = nameResolution[Ref(functionCall)]!!.value as Function
        val parameterNames = function.parameters.map { it.name }
        val isMatched = function.parameters.map { false }.toMutableList()

        if (functionCall.arguments.size > function.parameters.size) {
            report(Diagnostic.ResolutionDiagnostic.ArgumentResolutionError.TooManyArguments(functionCall))
            return
        }

        for ((index, argument) in functionCall.arguments.withIndex()) {
            if (argument.name == null) {
                argumentsToParametersMap[Ref(argument)] = Ref(function.parameters[index])
                isMatched[index] = true
            } else {
                val parameterIndex = parameterNames.indexOf(argument.name)
                if (parameterIndex == -1) {
                    report(Diagnostic.ResolutionDiagnostic.ArgumentResolutionError.UnknownArgument(function, functionCall, argument))
                    return
                }
                if (isMatched[parameterIndex]) {
                    report(Diagnostic.ResolutionDiagnostic.ArgumentResolutionError.RepeatedArgument(function, functionCall, function.parameters[parameterIndex]))
                    return
                }
                argumentsToParametersMap[Ref(argument)] = Ref(function.parameters[parameterIndex])
                isMatched[parameterIndex] = true
            }
        }

        val parametersWithDefaultValue = mutableListOf<Ref<Function.Parameter>>()

        for ((index, parameter) in function.parameters.withIndex()) {
            if (!isMatched[index]) {
                if (parameter.defaultValue == null) {
                    report(Diagnostic.ResolutionDiagnostic.ArgumentResolutionError.MissingArgument(function, functionCall, parameter))
                } else {
                    parametersWithDefaultValue.add(Ref(parameter))
                }
            }
        }
        accessedDefaultValues[Ref(functionCall)] = parametersWithDefaultValue.toSet()
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
                is Expression.ArrayElement -> TODO()
                is Expression.ArrayLength -> TODO()
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
                        is Statement.ForeachLoop -> {
                            processExpression(statement.generatorCall)
                            processBlock(statement.action)
                        }
                        is Statement.FunctionReturn -> processExpression(statement.value)
                        is Statement.GeneratorYield -> processExpression(statement.value)
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
                report(Diagnostic.ResolutionDiagnostic.ArgumentResolutionError.DefaultParametersNotLast(function))
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
                        is Statement.ForeachLoop -> {
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
