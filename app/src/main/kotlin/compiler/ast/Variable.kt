package compiler.ast

data class Variable(
    val kind: Kind,
    override val name: String,
    val type: Type,
    val value: Expression?
) : NamedNode {
    enum class Kind {
        CONSTANT,
        VALUE,
        VARIABLE
    }
}
