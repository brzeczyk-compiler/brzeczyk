package compiler.analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.utils.ReferenceSet
import compiler.utils.referenceHashSetOf
object BuiltinFunctions {
    val functionNapisz = Function(
        "napisz",
        listOf(Function.Parameter("wartość", Type.Number, null)),
        Type.Unit,
        Function.Implementation.Foreign("print")
    )

    val builtinFunctionsByName = mapOf(functionNapisz.name to functionNapisz)

    fun getUsedBuiltinFunctions(program: Program): ReferenceSet<Function> {
        val result = referenceHashSetOf<Function>()

        fun analyzeGlobal(global: Program.Global) {
            fun analyzeExpression(expression: Expression?) {
                fun analyzeCallArgument(node: Expression.FunctionCall.Argument) = analyzeExpression(node.value)
                when (expression) {
                    is Expression.FunctionCall -> {
                        if (builtinFunctionsByName.containsKey(expression.name))
                            result.add(builtinFunctionsByName[expression.name]!!)
                        expression.arguments.forEach { analyzeCallArgument(it) }
                    }

                    is Expression.UnaryOperation -> { analyzeExpression(expression.operand) }

                    is Expression.BinaryOperation -> {
                        analyzeExpression(expression.leftOperand)
                        analyzeExpression(expression.rightOperand)
                    }

                    is Expression.Conditional -> {
                        analyzeExpression(expression.condition)
                        analyzeExpression(expression.resultWhenTrue)
                        analyzeExpression(expression.resultWhenFalse)
                    }

                    is Expression.Variable -> {}
                    is Expression.BooleanLiteral -> {}
                    is Expression.NumberLiteral -> {}
                    is Expression.UnitLiteral -> {}
                    null -> {}
                }
            }

            fun analyzeVariable(node: Variable) { node.value?.let { analyzeExpression(it) } }
            fun analyzeParameter(node: Function.Parameter) { node.defaultValue?.let { analyzeExpression(it) } }
            fun analyzeFunction(node: Function, analyzeStatement: (Statement) -> Unit) {
                node.parameters.forEach { analyzeParameter(it) }
                node.body.forEach { analyzeStatement(it) }
            }

            fun analyzeStatement(statement: Statement) {
                when (statement) {
                    is Statement.Evaluation -> { analyzeExpression(statement.expression) }
                    is Statement.Assignment -> { analyzeExpression(statement.value) }
                    is Statement.Block -> { statement.block.forEach { analyzeStatement(it) } }

                    is Statement.Conditional -> {
                        analyzeExpression(statement.condition)
                        statement.actionWhenTrue.forEach { analyzeStatement(it) }
                        statement.actionWhenFalse?.forEach { analyzeStatement(it) }
                    }

                    is Statement.Loop -> {
                        analyzeExpression(statement.condition)
                        statement.action.forEach { analyzeStatement(it) }
                    }

                    is Statement.FunctionReturn -> { analyzeExpression(statement.value) }
                    is Statement.VariableDefinition -> { analyzeVariable(statement.variable) }
                    is Statement.FunctionDefinition -> { analyzeFunction(statement.function, ::analyzeStatement) }
                    is Statement.LoopBreak -> {}
                    is Statement.LoopContinuation -> {}
                }
            }

            when (global) {
                is Program.Global.VariableDefinition -> { analyzeVariable(global.variable) }
                is Program.Global.FunctionDefinition -> { analyzeFunction(global.function, ::analyzeStatement) }
            }
        }

        program.globals.forEach { analyzeGlobal(it) }
        return result
    }
}
