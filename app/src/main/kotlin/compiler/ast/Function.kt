package compiler.ast

import compiler.common.semantic_analysis.VariablesOwner
import compiler.lexer.LocationRange

data class Function(
    override val name: String,
    val parameters: List<Parameter>,
    val returnType: Type,
    val body: StatementBlock,
    override val location: LocationRange? = null,
) : NamedNode, AstNode, VariablesOwner {
    data class Parameter(
        override val name: String,
        val type: Type,
        val defaultValue: Expression?,
        override val location: LocationRange? = null,
    ) : NamedNode, AstNode
}
