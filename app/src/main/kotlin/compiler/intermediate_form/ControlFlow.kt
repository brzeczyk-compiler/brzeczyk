package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceMap

object ControlFlow {
    fun createGraphForExpression(expression: Expression, variable: Variable?): ControlFlowGraph {
        return TODO()
    }

    fun createGraphForEachFunction(
        ast: Program,
        graphForExpression: ReferenceMap<Expression, ControlFlowGraph>
    ): ReferenceMap<Function, ControlFlowGraph> {
        return TODO()
    }
}
