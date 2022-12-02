package compiler.ast

data class Variable(
    val kind: Kind,
    val name: String,
    val type: Type,
    val value: Expression?,
    override val location: NodeLocation? = null,
) : NamedNode, AstNode() {
    enum class Kind {
        CONSTANT,
        VALUE,
        VARIABLE,
    }
}
