package compiler.ast

import compiler.input.LocationRange

sealed interface AstNode {
    val location: LocationRange?

    fun toSimpleString(): String {
        val stringBuilder = StringBuilder()

        stringBuilder.append(
            when (this) {
                is Program -> "<<the entire program>>"
                is Program.Global -> "<<global definition>>"
                is Statement -> "<<statement>>"

                is Expression -> when (this) {
                    is Expression.BinaryOperation -> "${this.leftOperand.toSimpleString()} ${this.kind} ${this.rightOperand.toSimpleString()}"
                    is Expression.BooleanLiteral -> this.value.toString()
                    is Expression.Conditional -> "${this.condition.toSimpleString()} ? ${this.resultWhenTrue.toSimpleString()} : ${this.resultWhenFalse.toSimpleString()}"
                    is Expression.FunctionCall -> "${this.name}(${this.arguments.joinToString { it.toSimpleString() }})"
                    is Expression.NumberLiteral -> this.value.toString()
                    is Expression.UnaryOperation -> "${this.kind} ${this.operand}"
                    is Expression.UnitLiteral -> "nic"
                    is Expression.Variable -> this.name
                    is Expression.ArrayElement -> "${this.name}[${this.index}]"
                    is Expression.ArrayLength -> "długość ${this.expression}"
                }

                is Variable -> "${this.kind} ${this.name}: ${this.type}${if (this.value != null) "= ${this.value.toSimpleString()}" else ""}"
                is Function -> "czynność ${this.name}(${this.parameters.joinToString { it.toSimpleString() }}) -> ${this.returnType}"
                is Function.Parameter -> "${this.name}: ${this.type}${if (this.defaultValue != null) " = ${this.defaultValue.toSimpleString()}" else ""}"
                is Expression.FunctionCall.Argument -> "${if (this.name != null) "${this.name} = " else ""}${this.value.toSimpleString()}"
            }
        )

        return stringBuilder.toString()
    }

    fun toExtendedString(): String {
        val stringBuilder = StringBuilder()

        if (this.location != null) {
            stringBuilder.append("At location ${this.location}")
        } else {
            stringBuilder.append("At virtual location")
        }

        stringBuilder.append(" :: ")

        stringBuilder.append(
            when (this) {
                is Program -> "the entire program"

                is Program.Global -> when (this) {
                    is Program.Global.FunctionDefinition -> "definition of << ${this.function.toSimpleString()} >>"
                    is Program.Global.VariableDefinition -> "definition of << ${this.variable.toSimpleString()} >>"
                }

                is Expression -> "expression << ${this.toSimpleString()} >>"

                is Statement -> when (this) {
                    is Statement.Assignment -> {
                        lvalue.let {
                            when (it) {
                                is Statement.Assignment.LValue.Variable -> "assignment << ${it.name} = ${this.value.toSimpleString()} >>"
                                is Statement.Assignment.LValue.ArrayElement -> "assignment << ${it.name}[${it.index}] = ${this.value.toSimpleString()} >>"
                            }
                        }
                    }
                    is Statement.Block -> "{ ... }"
                    is Statement.Conditional -> "jeśli - zaś gdy - wpp block with the condition (${this.condition.toSimpleString()})"
                    is Statement.Evaluation -> "evaluation of << ${this.expression.toSimpleString()} >>"
                    is Statement.FunctionDefinition -> "definition of << ${this.function.toSimpleString()} >>"
                    is Statement.FunctionReturn -> "zwróć ${this.value}"
                    is Statement.Loop -> "dopóki (${this.condition}) { ... }"
                    is Statement.LoopBreak -> "przerwij"
                    is Statement.LoopContinuation -> "pomiń"
                    is Statement.VariableDefinition -> "definition of << ${this.variable.toSimpleString()} >>"
                    is Statement.ForeachLoop -> "otrzymując ${this.receivingVariable.toSimpleString()} od ${this.generatorCall.toSimpleString()} { ... }"
                    is Statement.GeneratorYield -> "przekaż ${this.value.toSimpleString()}"
                }

                is Variable -> "variable << ${this.toSimpleString()} >>"
                is Function -> "function << ${this.toSimpleString()} >>"
                is Function.Parameter -> "function parameter << ${this.toSimpleString()} >>"
                is Expression.FunctionCall.Argument -> "function argument << ${this.toSimpleString()} >>"
            }
        )

        return stringBuilder.toString()
    }
}
