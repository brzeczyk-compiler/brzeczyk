package compiler.ast

sealed class ExpressionAst {
    object UnitLiteral : ExpressionAst()
    data class BooleanLiteral(val value: Boolean) : ExpressionAst()
    data class NumberLiteral(val value: Int) : ExpressionAst()

    data class Variable(val name: String) : ExpressionAst()

    data class FunctionCall(
        val name: String,
        val arguments: List<Argument>
    ) : ExpressionAst() {
        data class Argument(
            val name: String?,
            val value: ExpressionAst
        )
    }

    data class UnaryOperation(
        val kind: Kind,
        val operand: ExpressionAst
    ) : ExpressionAst() {
        enum class Kind {
            NOT,
            NEG,
            BIT_NOT,
        }
    }

    data class BinaryOperation(
        val kind: Kind,
        val leftOperand: ExpressionAst,
        val rightOperand: ExpressionAst
    ) : ExpressionAst() {
        enum class Kind {
            AND,
            OR,
            ADD,
            SUB,
            MUL,
            DIV,
            MOD,
            BIT_AND,
            BIT_OR,
            BIT_XOR,
            BIT_SHL,
            BIT_SHR,
            EQ,
            NEQ,
            LT,
            LTEQ,
            GT,
            GTEQ,
        }
    }

    data class Conditional(
        val condition: ExpressionAst,
        val resultWhenTrue: ExpressionAst,
        val resultWhenFalse: ExpressionAst
    ) : ExpressionAst()
}
