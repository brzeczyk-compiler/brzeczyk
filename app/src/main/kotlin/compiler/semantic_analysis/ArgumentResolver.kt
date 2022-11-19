package compiler.semantic_analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceMap

typealias ArgumentResolutionResult = ReferenceMap<Expression.FunctionCall.Argument, Function.Parameter>

class ArgumentResolver(private val nameResolution: ReferenceMap<Any, NamedNode>, private val diagnostics: Diagnostics) {
    private val result = ReferenceHashMap<Expression.FunctionCall.Argument, Function.Parameter>()
    private var correctDefinitions = true

    companion object {
        fun calculateArgumentToParameterResolution(
            program: Program,
            nameResolution: ReferenceMap<Any, NamedNode>,
            diagnostics: Diagnostics
        ): ArgumentResolutionResult {
            val resolver = ArgumentResolver(nameResolution, diagnostics)

            resolver.checkFunctionDefinitions(program)
            if (resolver.correctDefinitions)
                resolver.checkFunctionCalls(program)

            return resolver.result
        }
    }

    private fun checkFunctionCall(functionCall: Expression.FunctionCall) {
        var foundNamedArgument = false
        for (argument in functionCall.arguments) {
            if (argument.name != null) {
                foundNamedArgument = true
            } else if (foundNamedArgument) {
                diagnostics.report(Diagnostic.ArgumentResolutionError.PositionalArgumentAfterNamed(functionCall))
                return
            }
        }

        val function: Function = nameResolution[functionCall] as Function
        val parameterNames = function.parameters.map { it.name }
        val numberOfParameters = parameterNames.size
        val isMatched = parameterNames.map { false }.toMutableList()

        for ((index, argument) in functionCall.arguments.withIndex()) {
            if (argument.name == null) {
                if (index >= numberOfParameters) {
                    diagnostics.report(Diagnostic.ArgumentResolutionError.TooManyArguments(functionCall))
                    return
                }
                result[argument] = function.parameters[index]
                isMatched[index] = true
            } else {
                val parameterIndex = parameterNames.indexOf(argument.name)
                if (parameterIndex == -1) {
                    diagnostics.report(Diagnostic.ArgumentResolutionError.UnknownArgument(functionCall, argument.name))
                    return
                }
                if (isMatched[parameterIndex]) {
                    diagnostics.report(Diagnostic.ArgumentResolutionError.RepeatedArgument(functionCall, argument.name))
                    return
                }
                result[argument] = function.parameters[parameterIndex]
                isMatched[parameterIndex] = true
            }
        }

        for (index in 0 until numberOfParameters) {
            if (!isMatched[index] && function.parameters[index].defaultValue == null) {
                diagnostics.report(Diagnostic.ArgumentResolutionError.MissingArgument(functionCall, parameterNames[index]))
            }

            // TODO: detect default parameter values used in this function call
        }
    }

    private fun checkExpression(expression: Expression) {
        when (expression) {
            is Expression.FunctionCall -> {
                checkFunctionCall(expression)
            }
            is Expression.UnaryOperation -> {
                checkExpression(expression.operand)
            }
            is Expression.BinaryOperation -> {
                checkExpression(expression.leftOperand)
                checkExpression(expression.rightOperand)
            }
            is Expression.Conditional -> {
                checkExpression(expression.condition)
                checkExpression(expression.resultWhenTrue)
                checkExpression(expression.resultWhenFalse)
            }
        }
    }

    private fun checkVariable(variable: Variable) {
        if (variable.value != null) {
            checkExpression(variable.value)
        }
    }

    private fun checkFunctionCalls(program: Program) {
        fun checkFunction(function: Function) {
            fun checkBlock(block: StatementBlock) {
                for (statement in block) {
                    when (statement) {
                        is Statement.Evaluation -> checkExpression(statement.expression)
                        is Statement.VariableDefinition -> checkVariable(statement.variable)
                        is Statement.FunctionDefinition -> checkFunction(statement.function)
                        is Statement.Assignment -> checkExpression(statement.value)
                        is Statement.Block -> checkBlock(statement.block)
                        is Statement.Conditional -> {
                            checkExpression(statement.condition)
                            checkBlock(statement.actionWhenTrue)
                            statement.actionWhenFalse?.let { checkBlock(it) }
                        }
                        is Statement.Loop -> {
                            checkExpression(statement.condition)
                            checkBlock(statement.action)
                        }
                        is Statement.FunctionReturn -> checkExpression(statement.value)
                    }
                }
            }

            checkBlock(function.body)
        }

        program.globals.forEach { if (it is Program.Global.FunctionDefinition) checkFunction(it.function) }
    }

    private fun checkFunctionDefinitions(program: Program) {
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

        fun checkFunction(function: Function, global: Boolean) {
            if (defaultParametersBeforeNonDefault(function)) {
                diagnostics.report(Diagnostic.ArgumentResolutionError.DefaultParametersNotLast(function))
            }

            // TODO: generate variables for default parameters

            fun checkBlock(block: StatementBlock) {
                for (statement in block) {
                    when (statement) {
                        is Statement.FunctionDefinition -> checkFunction(statement.function, false)
                        is Statement.Block -> checkBlock(statement.block)
                        is Statement.Conditional -> {
                            checkBlock(statement.actionWhenTrue)
                            statement.actionWhenFalse?.let { checkBlock(it) }
                        }
                        is Statement.Loop -> {
                            checkBlock(statement.action)
                        }
                    }
                }
            }

            checkBlock(function.body)
        }

        program.globals.forEach { if (it is Program.Global.FunctionDefinition) checkFunction(it.function, true) }
    }
}
