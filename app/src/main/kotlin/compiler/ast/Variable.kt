package compiler.ast

data class Variable(
    val kind: Kind,
    val name: String,
    val type: Type,
    val value: Expression?
) {
    enum class Kind {
        CONSTANT,
        VALUE,
        VARIABLE
    }
}
