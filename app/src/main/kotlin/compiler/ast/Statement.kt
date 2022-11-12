package compiler.ast

typealias StatementBlock = List<Statement>

sealed class Statement {
    data class Evaluation(val expression: Expression) : Statement()

    data class VariableDefinition(val variable: Variable) : Statement()
    data class FunctionDefinition(val function: Function) : Statement()

    data class Assignment(
        val variableName: String,
        val value: Expression
    ) : Statement()

    data class Block(val block: StatementBlock) : Statement()

    data class Conditional(
        val condition: Expression,
        val actionWhenTrue: StatementBlock,
        val actionWhenFalse: StatementBlock?
    ) : Statement()

    data class Loop(
        val condition: Expression,
        val action: StatementBlock
    ) : Statement()

    object LoopBreak : Statement()

    object LoopContinuation : Statement()

    data class FunctionReturn(val value: Expression) : Statement()
}
