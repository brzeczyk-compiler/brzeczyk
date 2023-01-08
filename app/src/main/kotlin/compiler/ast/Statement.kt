package compiler.ast

import compiler.input.LocationRange

typealias StatementBlock = List<Statement>

sealed class Statement : AstNode {
    data class Evaluation(
        val expression: Expression,
        override val location: LocationRange? = null,
    ) : Statement()

    data class VariableDefinition(
        val variable: Variable,
        override val location: LocationRange? = null,
    ) : Statement()

    data class FunctionDefinition(
        val function: Function,
        override val location: LocationRange? = null,
    ) : Statement()

    data class Assignment(
        val variableName: String,
        val value: Expression,
        override val location: LocationRange? = null,
    ) : Statement()

    data class Block(
        val block: StatementBlock,
        override val location: LocationRange? = null,
    ) : Statement()

    data class Conditional(
        val condition: Expression,
        val actionWhenTrue: StatementBlock,
        val actionWhenFalse: StatementBlock?,
        override val location: LocationRange? = null,
    ) : Statement()

    data class Loop(
        val condition: Expression,
        val action: StatementBlock,
        override val location: LocationRange? = null,
    ) : Statement()

    data class LoopBreak(
        override val location: LocationRange? = null,
    ) : Statement()

    data class LoopContinuation(
        override val location: LocationRange? = null,
    ) : Statement()

    data class ForeachLoop(
        val receivingVariable: Variable,
        val generatorCall: Expression.FunctionCall,
        val action: StatementBlock,
        override val location: LocationRange? = null,
    ) : Statement()

    data class FunctionReturn(
        val value: Expression,
        val isWithoutExplicitlySpecifiedValue: Boolean = false,
        override val location: LocationRange? = null,
    ) : Statement()

    data class GeneratorYield(
        val value: Expression,
        override val location: LocationRange? = null,
    ) : Statement()
}
