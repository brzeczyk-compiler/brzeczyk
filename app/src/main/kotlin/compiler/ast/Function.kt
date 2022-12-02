package compiler.ast

import compiler.common.ast.VariablesOwner

data class Function(
    val name: String,
    val parameters: List<Parameter>,
    val returnType: Type,
    val body: StatementBlock
) : NamedNode, VariablesOwner {
    data class Parameter(
        val name: String,
        val type: Type,
        val defaultValue: Expression?
    ) : NamedNode
}
