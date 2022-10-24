package compiler.ast

data class FunctionAst(
    val name: String,
    val parameters: List<Parameter>,
    val returnType: TypeAst?,
    val body: BlockAst
) {
    data class Parameter(
        val name: String,
        val type: TypeAst,
        val defaultValue: ExpressionAst?
    )
}
