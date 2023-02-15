package compiler.ast

import compiler.input.LocationRange

typealias StatementBlock = List<Statement>

sealed class Statement : AstNode {
    data class Evaluation(
        val expression: Expression,
        override val location: LocationRange? = null,
    ) : Statement()

    data class VariableDefinition(
        val variable: Variable,
        override val location: LocationRange? = null,
    ) : Statement()

    data class FunctionDefinition(
        val function: Function,
        override val location: LocationRange? = null,
    ) : Statement()

    data class Assignment(
        val lvalue: LValue,
        val value: Expression,
        override val location: LocationRange? = null,
    ) : Statement() {
        sealed class LValue() {
            data class Variable(val name: String) : LValue()
            data class ArrayElement(val expression: Expression, val index: Expression) : LValue()
        }
    }

    data class Block(
        val block: StatementBlock,
        override val location: LocationRange? = null,
    ) : Statement(), IterationResult

    data class Conditional(
        val condition: Expression,
        val actionWhenTrue: StatementBlock,
        val actionWhenFalse: StatementBlock?,
        override val location: LocationRange? = null,
    ) : Statement()

    data class Loop(
        val condition: Expression,
        val action: StatementBlock,
        override val location: LocationRange? = null,
    ) : Statement()

    data class LoopBreak(
        override val location: LocationRange? = null,
    ) : Statement()

    data class LoopContinuation(
        override val location: LocationRange? = null,
    ) : Statement()

    data class ForeachLoop(
        val receivingVariable: Variable,
        val generatorCall: Expression.FunctionCall,
        val action: StatementBlock,
        override val location: LocationRange? = null,
    ) : Statement(), IterationResult

    data class FunctionReturn(
        val value: Expression,
        val isWithoutExplicitlySpecifiedValue: Boolean = false,
        override val location: LocationRange? = null,
    ) : Statement()

    data class GeneratorYield(
        val value: Expression,
        override val location: LocationRange? = null,
    ) : Statement()

    override fun toSimpleString() = "<<statement>>"

    override fun toExtendedString() = when (this) {
        is Assignment -> {
            lvalue.let {
                when (it) {
                    is Assignment.LValue.Variable -> "assignment << ${it.name} = ${this.value.toSimpleString()} >>"
                    is Assignment.LValue.ArrayElement -> "assignment << ${it.expression}[${it.index.toSimpleString()}] = ${this.value.toSimpleString()} >>"
                }
            }
        }
        is Block -> "{ ... }"
        is Conditional -> "jeśli - zaś gdy - wpp block with the condition (${this.condition.toSimpleString()})"
        is Evaluation -> "evaluation of << ${this.expression.toSimpleString()} >>"
        is FunctionDefinition -> "definition of << ${this.function.toSimpleString()} >>"
        is FunctionReturn -> "zwróć ${this.value}"
        is Loop -> "dopóki (${this.condition}) { ... }"
        is LoopBreak -> "przerwij"
        is LoopContinuation -> "pomiń"
        is VariableDefinition -> "definition of << ${this.variable.toSimpleString()} >>"
        is ForeachLoop -> "otrzymując ${this.receivingVariable.toSimpleString()} od ${this.generatorCall.toSimpleString()} { ... }"
        is GeneratorYield -> "przekaż ${this.value.toSimpleString()}"
    }
}
