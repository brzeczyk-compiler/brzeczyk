package compiler.ast

data class Function(
    val name: String,
    val parameters: List<Parameter>,
    val returnType: Type,
    val body: Block
) : NamedNode {
    data class Parameter(
        val name: String,
        val type: Type,
        val defaultValue: Expression?
    ) : NamedNode
}
