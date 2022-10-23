package compiler.ast

data class VariableAst(
    val kind: Kind,
    val name: String,
    val type: TypeAst,
    val value: ExpressionAst?
) {
    enum class Kind {
        CONSTANT,
        VALUE,
        VARIABLE
    }
}
