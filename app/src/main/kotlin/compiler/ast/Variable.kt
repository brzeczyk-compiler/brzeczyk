package compiler.ast

import compiler.lexer.LocationRange

data class Variable(
    val kind: Kind,
    val name: String,
    val type: Type,
    val value: Expression?,
    override val location: LocationRange? = null,
) : NamedNode, AstNode {
    enum class Kind {
        CONSTANT,
        VALUE,
        VARIABLE,
    }
}
