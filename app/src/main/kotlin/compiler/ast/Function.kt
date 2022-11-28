package compiler.ast

data class Function(
    override val name: String,
    val parameters: List<Parameter>,
    val returnType: Type,
    val body: StatementBlock
) : NamedNode {
    data class Parameter(
        override val name: String,
        val type: Type,
        val defaultValue: Expression?
    ) : NamedNode
}
