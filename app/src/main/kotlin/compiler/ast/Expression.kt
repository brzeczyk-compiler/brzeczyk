package compiler.ast

sealed class Expression {
    object UnitLiteral : Expression()
    data class BooleanLiteral(val value: Boolean) : Expression()
    data class NumberLiteral(val value: Int) : Expression()

    data class Variable(val name: String) : Expression()

    data class FunctionCall(
        val name: String,
        val arguments: List<Argument>
    ) : Expression() {
        data class Argument(
            val name: String?,
            val value: Expression
        )
    }

    data class UnaryOperation(
        val kind: Kind,
        val operand: Expression
    ) : Expression() {
        enum class Kind {
            NOT,
            PLUS,
            MINUS,
            BIT_NOT
        }
    }

    data class BinaryOperation(
        val kind: Kind,
        val leftOperand: Expression,
        val rightOperand: Expression
    ) : Expression() {
        enum class Kind {
            AND,
            OR,
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
            GREATER_THAN_OR_EQUALS
        }
    }

    data class Conditional(
        val condition: Expression,
        val resultWhenTrue: Expression,
        val resultWhenFalse: Expression
    ) : Expression()
}
