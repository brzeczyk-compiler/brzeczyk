package compiler.ast

import compiler.lexer.LocationRange
import compiler.common.semantic_analysis.VariablesOwner

data class Function(
    val name: String,
    val parameters: List<Parameter>,
    val returnType: Type,
    val body: StatementBlock,
    override val location: LocationRange? = null,
) : NamedNode, AstNode, VariablesOwner {
    data class Parameter(
        val name: String,
        val type: Type,
        val defaultValue: Expression?,
        override val location: LocationRange? = null,
    ) : NamedNode, AstNode
}
