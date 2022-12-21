package compiler.ast

import compiler.input.LocationRange

data class Function(
    override val name: String,
    val parameters: List<Parameter>,
    val returnType: Type,
    val body: StatementBlock,
    override val location: LocationRange? = null,
) : NamedNode, AstNode, VariableOwner {
    data class Parameter(
        override val name: String,
        val type: Type,
        val defaultValue: Expression?,
        override val location: LocationRange? = null,
    ) : NamedNode, AstNode
}
