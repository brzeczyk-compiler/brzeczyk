package compiler.analysis

import compiler.Compiler.CompilationFailed
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Type
import compiler.ast.Variable
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.TypeCheckingError
import compiler.diagnostics.Diagnostics
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf

class TypeChecker(private val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>, private val argumentResolution: ArgumentResolutionResult, private val diagnostics: Diagnostics) {
    private val expressionTypes = mutableKeyRefMapOf<Expression, Type>()
    private var failed = false

    class TypeCheckingFailed : CompilationFailed()

    companion object {
        fun calculateTypes(program: Program, nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>, argumentResolution: ArgumentResolutionResult, diagnostics: Diagnostics): Map<Ref<Expression>, Type> {
            val checker = TypeChecker(nameResolution, argumentResolution, diagnostics)

            checker.checkProgram(program)

            if (checker.failed)
                throw TypeCheckingFailed()

            return checker.expressionTypes
        }
    }

    private fun report(diagnostic: TypeCheckingError) {
        diagnostics.report(diagnostic)
        failed = true
    }

    private fun checkProgram(program: Program) {
        for (global in program.globals) {
            when (global) {
                is Program.Global.VariableDefinition -> checkVariable(global.variable, true)
                is Program.Global.FunctionDefinition -> checkFunction(global.function, true)
            }
        }
    }

    private fun checkVariable(variable: Variable, global: Boolean) {
        if (variable.value != null) {
            if (variable.kind == Variable.Kind.CONSTANT || global)
                checkConstantExpression(variable.value, variable.type)
            else
                checkExpression(variable.value, variable.type)
        } else if (variable.kind == Variable.Kind.CONSTANT)
            report(TypeCheckingError.ConstantWithoutValue(variable))
        else if (global)
            report(TypeCheckingError.UninitializedGlobalVariable(variable))
    }

    private fun checkFunction(function: Function, global: Boolean) {
        for (parameter in function.parameters) {
            if (parameter.defaultValue == null)
                continue
            if (global)
                checkConstantExpression(parameter.defaultValue, parameter.type)
            else
                checkExpression(parameter.defaultValue, parameter.type)
        }

        fun checkBlock(block: StatementBlock) {
            for (statement in block) {
                when (statement) {
                    is Statement.Evaluation -> checkExpression(statement.expression)

                    is Statement.VariableDefinition -> checkVariable(statement.variable, false)

                    is Statement.FunctionDefinition -> checkFunction(statement.function, false)

                    is Statement.Assignment -> {
                        when (val node = nameResolution[Ref(statement)]!!.value) {
                            is Variable -> {
                                if (node.kind != Variable.Kind.VARIABLE)
                                    report(TypeCheckingError.ImmutableAssignment(statement, node))
                                checkExpression(statement.value, node.type)
                            }

                            is Function.Parameter -> report(TypeCheckingError.ParameterAssignment(statement, node))

                            is Function -> report(TypeCheckingError.FunctionAssignment(statement, node))
                        }
                    }

                    is Statement.Block -> checkBlock(statement.block)

                    is Statement.Conditional -> {
                        checkExpression(statement.condition, Type.Boolean)
                        checkBlock(statement.actionWhenTrue)
                        statement.actionWhenFalse?.let { checkBlock(it) }
                    }

                    is Statement.Loop -> {
                        checkExpression(statement.condition, Type.Boolean)
                        checkBlock(statement.action)
                    }

                    is Statement.LoopBreak -> { } // TODO: check if inside a loop
                    is Statement.LoopContinuation -> { } // TODO: check if inside a loop

                    is Statement.FunctionReturn -> checkExpression(statement.value, if (function.isGenerator) Type.Unit else function.returnType)
                    is Statement.ForeachLoop -> {
                        checkVariable(statement.receivingVariable, false)
                        checkExpression(statement.generatorCall, statement.receivingVariable.type)
                        checkBlock(statement.action)
                    }
                    is Statement.GeneratorYield -> {
                        checkExpression(statement.value, function.returnType)
                    }
                }
            }
        }

        fun checkIfLastStatementIsReturn(block: StatementBlock) {
            if (block.isEmpty())
                report(TypeCheckingError.MissingReturnStatement(function))
            else {
                when (val lastStatement = block.last()) {
                    is Statement.FunctionReturn -> return
                    is Statement.Block -> checkIfLastStatementIsReturn(lastStatement.block)
                    is Statement.Conditional -> {
                        checkIfLastStatementIsReturn(lastStatement.actionWhenTrue)
                        if (lastStatement.actionWhenFalse == null) // obligatory else
                            report(TypeCheckingError.MissingReturnStatement(function))
                        else
                            checkIfLastStatementIsReturn(lastStatement.actionWhenFalse)
                    }
                    else -> { report(TypeCheckingError.MissingReturnStatement(function)) }
                }
            }
        }

        checkBlock(function.body)

        if (function.implementation is Function.Implementation.Local && function.returnType != Type.Unit && !function.isGenerator)
            checkIfLastStatementIsReturn(function.body)
    }

    private fun checkExpression(expression: Expression): Type? {
        fun check(): Type? {
            when (expression) {
                is Expression.UnitLiteral -> return Type.Unit

                is Expression.BooleanLiteral -> return Type.Boolean

                is Expression.NumberLiteral -> return Type.Number

                is Expression.Variable -> {
                    when (val node = nameResolution[Ref(expression)]!!.value) {
                        is Variable -> return node.type

                        is Function.Parameter -> return node.type

                        is Function -> report(TypeCheckingError.FunctionAsValue(expression, node))
                    }
                }

                is Expression.FunctionCall -> {
                    when (val node = nameResolution[Ref(expression)]!!.value) {
                        is Variable -> report(TypeCheckingError.VariableCall(expression, node))

                        is Function.Parameter -> report(TypeCheckingError.ParameterCall(expression, node))

                        is Function -> {
                            for (argument in expression.arguments) {
                                val parameter = argumentResolution[Ref(argument)]!!.value
                                checkExpression(argument.value, parameter.type)
                            }

                            return node.returnType
                        }
                    }
                }

                is Expression.UnaryOperation -> {
                    when (expression.kind) {
                        Expression.UnaryOperation.Kind.NOT -> {
                            checkExpression(expression.operand, Type.Boolean)
                            return Type.Boolean
                        }

                        Expression.UnaryOperation.Kind.PLUS,
                        Expression.UnaryOperation.Kind.MINUS,
                        Expression.UnaryOperation.Kind.BIT_NOT -> {
                            checkExpression(expression.operand, Type.Number)
                            return Type.Number
                        }
                    }
                }

                is Expression.BinaryOperation -> {
                    when (expression.kind) {
                        Expression.BinaryOperation.Kind.AND,
                        Expression.BinaryOperation.Kind.OR,
                        Expression.BinaryOperation.Kind.IFF,
                        Expression.BinaryOperation.Kind.XOR -> {
                            checkExpression(expression.leftOperand, Type.Boolean)
                            checkExpression(expression.rightOperand, Type.Boolean)
                            return Type.Boolean
                        }

                        Expression.BinaryOperation.Kind.ADD,
                        Expression.BinaryOperation.Kind.SUBTRACT,
                        Expression.BinaryOperation.Kind.MULTIPLY,
                        Expression.BinaryOperation.Kind.DIVIDE,
                        Expression.BinaryOperation.Kind.MODULO,
                        Expression.BinaryOperation.Kind.BIT_AND,
                        Expression.BinaryOperation.Kind.BIT_OR,
                        Expression.BinaryOperation.Kind.BIT_XOR,
                        Expression.BinaryOperation.Kind.BIT_SHIFT_LEFT,
                        Expression.BinaryOperation.Kind.BIT_SHIFT_RIGHT -> {
                            checkExpression(expression.leftOperand, Type.Number)
                            checkExpression(expression.rightOperand, Type.Number)
                            return Type.Number
                        }

                        Expression.BinaryOperation.Kind.EQUALS,
                        Expression.BinaryOperation.Kind.NOT_EQUALS,
                        Expression.BinaryOperation.Kind.LESS_THAN,
                        Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS,
                        Expression.BinaryOperation.Kind.GREATER_THAN,
                        Expression.BinaryOperation.Kind.GREATER_THAN_OR_EQUALS -> {
                            checkExpression(expression.leftOperand, Type.Number)
                            checkExpression(expression.rightOperand, Type.Number)
                            return Type.Boolean
                        }
                    }
                }

                is Expression.Conditional -> {
                    checkExpression(expression.condition, Type.Boolean)

                    val typeWhenTrue = checkExpression(expression.resultWhenTrue)
                    val typeWhenFalse = checkExpression(expression.resultWhenFalse)

                    if (typeWhenTrue == typeWhenFalse)
                        return typeWhenTrue
                    else if (typeWhenTrue != null && typeWhenFalse != null)
                        report(TypeCheckingError.ConditionalTypesMismatch(expression, typeWhenTrue, typeWhenFalse))
                }
            }

            return null
        }

        return check()?.also { expressionTypes[Ref(expression)] = it }
    }

    private fun checkExpression(expression: Expression, expectedType: Type) {
        val type = checkExpression(expression)
        if (type != null && type != expectedType)
            report(TypeCheckingError.InvalidType(expression, type, expectedType))
    }

    private fun checkConstantExpression(expression: Expression): Type? {
        fun check(): Type? {
            when (expression) {
                is Expression.UnitLiteral -> return Type.Unit

                is Expression.BooleanLiteral -> return Type.Boolean

                is Expression.NumberLiteral -> return Type.Number

                is Expression.Variable,
                is Expression.FunctionCall,
                is Expression.UnaryOperation,
                is Expression.BinaryOperation,
                is Expression.Conditional -> {
                    // TODO: some of these could be considered as constant
                    // in case of change, update Expression.getValueOfLiteral as well
                    report(TypeCheckingError.NonConstantExpression(expression))
                }
            }

            return null
        }

        return check()?.also { expressionTypes[Ref(expression)] = it }
    }

    private fun checkConstantExpression(expression: Expression, expectedType: Type) {
        val type = checkConstantExpression(expression)
        if (type != null && type != expectedType)
            report(TypeCheckingError.InvalidType(expression, type, expectedType))
    }
}
