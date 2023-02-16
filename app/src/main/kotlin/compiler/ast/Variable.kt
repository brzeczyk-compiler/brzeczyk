package compiler.ast

import compiler.input.LocationRange

data class Variable(
    val kind: Kind,
    override val name: String,
    val type: Type,
    val value: Expression?,
    override val location: LocationRange? = null,
) : NamedNode, AstNode {
    enum class Kind {
        CONSTANT,
        VALUE,
        VARIABLE;

        override fun toString(): String = when (this) {
            CONSTANT -> "stała"
            VALUE -> "wart"
            VARIABLE -> "zm"
        }
    }

    override fun toSimpleString() = "${this.kind} ${this.name}: ${this.type}${if (this.value != null) " = ${this.value.toSimpleString()}" else ""}"
    override fun toExtendedString() = "variable << ${this.toSimpleString()} >>"
}
