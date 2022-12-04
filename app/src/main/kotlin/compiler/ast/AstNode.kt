package compiler.ast

import compiler.lexer.LocationRange

sealed interface AstNode {
    val location: LocationRange?

    fun printSimple(): String {
        val stringBuilder = StringBuilder()

        stringBuilder.append(
            when (this) {
                is Program.Global -> "<<global definition>>"
                is Statement -> "<<statement>>"

                is Expression -> when (this) {
                    is Expression.BinaryOperation -> "${this.leftOperand.printSimple()} ${this.kind} ${this.rightOperand.printSimple()}"
                    is Expression.BooleanLiteral -> this.value.toString()
                    is Expression.Conditional -> "${this.condition.printSimple()} ? ${this.resultWhenTrue.printSimple()} : ${this.resultWhenFalse.printSimple()}"
                    is Expression.FunctionCall -> "${this.name}(${this.arguments.joinToString { it.printSimple() }})"
                    is Expression.NumberLiteral -> this.value.toString()
                    is Expression.UnaryOperation -> "${this.kind} ${this.operand}"
                    is Expression.UnitLiteral -> "nic"
                    is Expression.Variable -> this.name
                }

                is Variable -> "${this.kind} ${this.name}: ${this.type}${if (this.value != null) "= ${this.value.printSimple()}" else ""}"
                is Function -> "czynność ${this.name}(${this.parameters.joinToString { it.printSimple() }}) -> ${this.returnType}"
                is Function.Parameter -> "${this.name}: ${this.type}${if (this.defaultValue != null) " = ${this.defaultValue.printSimple()}" else ""}"
                is Expression.FunctionCall.Argument -> "${if (this.name != null) "${this.name} = " else ""}${this.value.printSimple()}"
            }
        )

        return stringBuilder.toString()
    }

    fun print(): String {
        val stringBuilder = StringBuilder()

        if (this.location != null) {
            stringBuilder.append("at location ${this.location}")
        } else {
            stringBuilder.append("at virtual location")
        }

        stringBuilder.append(" :: ")

        stringBuilder.append(
            when (this) {
                is Program.Global -> when (this) {
                    is Program.Global.FunctionDefinition -> "definition of ${this.function.printSimple()}"
                    is Program.Global.VariableDefinition -> "definition of ${this.variable.printSimple()}"
                }

                is Expression -> "expression <<${this.printSimple()}>>"

                is Statement -> when (this) {
                    is Statement.Assignment -> "assignment ${this.variableName} = ${this.value.printSimple()}"
                    is Statement.Block -> "{ ... }"
                    is Statement.Conditional -> "jeśli - zaś gdy - wpp block with the condition (${this.condition.printSimple()})"
                    is Statement.Evaluation -> "evaluation of ${this.expression.printSimple()}"
                    is Statement.FunctionDefinition -> "definition of ${this.function.printSimple()}"
                    is Statement.FunctionReturn -> "zwróć ${this.value}"
                    is Statement.Loop -> "dopóki (${this.condition}) { ... }"
                    is Statement.LoopBreak -> "przerwij"
                    is Statement.LoopContinuation -> "pomiń"
                    is Statement.VariableDefinition -> "definition of ${this.variable.printSimple()}"
                }

                is Variable -> "variable ${this.printSimple()}"
                is Function -> "function ${this.printSimple()}"
                is Function.Parameter -> "function parameter ${this.printSimple()}"
                is Expression.FunctionCall.Argument -> "function argument ${this.printSimple()}"
            }
        )

        return stringBuilder.toString()
    }
}
