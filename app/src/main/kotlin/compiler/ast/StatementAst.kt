package compiler.ast

typealias BlockAst = List<StatementAst>

sealed class StatementAst {
    data class Evaluation(val expression: ExpressionAst) : StatementAst()

    data class VariableDefinition(val variable: VariableAst) : StatementAst()
    data class FunctionDefinition(val function: FunctionAst) : StatementAst()

    data class Assignment(
        val variableName: String,
        val value: ExpressionAst
    ) : StatementAst()

    data class Block(val block: BlockAst) : StatementAst()

    data class Conditional(
        val condition: ExpressionAst,
        val actionWhenTrue: BlockAst,
        val actionWhenFalse: BlockAst?
    ) : StatementAst()

    data class Loop(
        val condition: ExpressionAst,
        val action: BlockAst
    ) : StatementAst()

    object LoopBreak : StatementAst()

    object LoopContinuation : StatementAst()

    data class FunctionReturn(val value: ExpressionAst?) : StatementAst()
}
