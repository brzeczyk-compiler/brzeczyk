package compiler.ast

import compiler.input.LocationRange
import compiler.intermediate.IFTNode

sealed class Expression : AstNode {
    data class UnitLiteral(
        override val location: LocationRange? = null,
    ) : Expression()

    data class BooleanLiteral(
        val value: Boolean,
        override val location: LocationRange? = null,
    ) : Expression()

    data class NumberLiteral(
        val value: Long,
        override val location: LocationRange? = null,
    ) : Expression()

    data class Variable(
        val name: String,
        override val location: LocationRange? = null,
    ) : Expression()

    data class ArrayElement(
        val expression: Expression,
        val index: Expression,
        override val location: LocationRange? = null
    ) : Expression()

    data class ArrayLength(
        val expression: Expression,
        override val location: LocationRange? = null
    ) : Expression()

    data class ArrayAllocation(
        val type: String,
        val size: Expression,
        val initialization: List<Expression>,
        val initializationType: InitializationType,
        override val location: LocationRange? = null
    ) : Expression() {
        enum class InitializationType {
            ONE_VALUE,
            ALL_VALUES
        }
    }

    data class FunctionCall(
        val name: String,
        val arguments: List<Argument>,
        override val location: LocationRange? = null,
    ) : Expression() {
        data class Argument(
            val name: String?,
            val value: Expression,
            override val location: LocationRange? = null,
        ) : AstNode
    }

    data class UnaryOperation(
        val kind: Kind,
        val operand: Expression,
        override val location: LocationRange? = null,
    ) : Expression() {
        enum class Kind {
            NOT,
            PLUS,
            MINUS,
            BIT_NOT;

            override fun toString(): String = when (this) {
                NOT -> "nie"
                PLUS -> "+"
                MINUS -> "-"
                BIT_NOT -> "~"
            }
        }
    }

    data class BinaryOperation(
        val kind: Kind,
        val leftOperand: Expression,
        val rightOperand: Expression,
        override val location: LocationRange? = null,
    ) : Expression() {
        enum class Kind {
            AND,
            OR,
            IFF,
            XOR,
            ADD,
            SUBTRACT,
            MULTIPLY,
            DIVIDE,
            MODULO,
            BIT_AND,
            BIT_OR,
            BIT_XOR,
            BIT_SHIFT_LEFT,
            BIT_SHIFT_RIGHT,
            EQUALS,
            NOT_EQUALS,
            LESS_THAN,
            LESS_THAN_OR_EQUALS,
            GREATER_THAN,
            GREATER_THAN_OR_EQUALS;

            override fun toString(): String = when (this) {
                AND -> "oraz"
                OR -> "lub"
                IFF -> "wtw"
                XOR -> "albo"
                ADD -> "+"
                SUBTRACT -> "-"
                MULTIPLY -> "*"
                DIVIDE -> "/"
                MODULO -> "%"
                BIT_AND -> "&"
                BIT_OR -> "|"
                BIT_XOR -> "^"
                BIT_SHIFT_LEFT -> "<<"
                BIT_SHIFT_RIGHT -> ">>"
                EQUALS -> "=="
                NOT_EQUALS -> "!="
                LESS_THAN -> "<"
                LESS_THAN_OR_EQUALS -> "<="
                GREATER_THAN -> ">"
                GREATER_THAN_OR_EQUALS -> ">="
            }
        }
    }

    data class Conditional(
        val condition: Expression,
        val resultWhenTrue: Expression,
        val resultWhenFalse: Expression,
        override val location: LocationRange? = null,
    ) : Expression()

    companion object {
        fun getValueOfLiteral(expr: Expression) = when (expr) {
            is UnitLiteral -> IFTNode.UNIT_VALUE
            is BooleanLiteral -> if (expr.value) 1L else 0L
            is NumberLiteral -> expr.value
            else -> null
        }
    }
}
