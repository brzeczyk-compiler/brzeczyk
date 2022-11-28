package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostics
import compiler.common.reference_collections.referenceMapOf
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FunctionDetailsGeneratorTest {
    private val function42 = Function(
        "return42",
        listOf(),
        Type.Number,
        listOf(
            Statement.FunctionReturn(Expression.NumberLiteral(42))
        )
    )

    private val expressionToCfgMock = { expression: Expression, _: Variable? ->
        when (expression) {
            is Expression.NumberLiteral -> {
                val root = IntermediateFormTreeNode.Const(expression.value.toLong())
                ControlFlowGraphBuilder(root).build()
            }
            else -> throw RuntimeException("Incorrect expression type")
        }
    }

    private val function42CFG = ControlFlow.createGraphForEachFunction(
        Program(listOf(Program.Global.FunctionDefinition(function42))),
        expressionToCfgMock,
        referenceMapOf(),
        referenceMapOf(),
        Diagnostics {}
    )[function42]!!

    private val function42NoReturn = Function(
        "return42",
        listOf(),
        Type.Unit,
        listOf(
            Statement.Evaluation(Expression.NumberLiteral(42))
        )
    )

    private val function42NoReturnCFG = ControlFlow.createGraphForEachFunction(
        Program(listOf(Program.Global.FunctionDefinition(function42NoReturn))),
        expressionToCfgMock,
        referenceMapOf(),
        referenceMapOf(),
        Diagnostics {}
    )[function42NoReturn]!!

    @Test
    fun `test genCall for non unit returning function`() {
        val fdg = FunctionDetailsGenerator(
            listOf(),
            function42CFG,
            function42
        )
        val result = fdg.genCall(listOf())

        val root = result.callGraph.entryTreeRoot as IntermediateFormTreeNode.RegisterWrite // should not fail
        val subNode = root.node as IntermediateFormTreeNode.Const // should not fail
        val resultNode = result.result as IntermediateFormTreeNode.RegisterRead // should not fail

        assertEquals(listOf(root), result.callGraph.treeRoots)
        assertEquals(42, subNode.value)

        assertEquals(resultNode.register, root.register) 
    }

    @Test
    fun `test genCall for unit returning function`() {
        val fdg = FunctionDetailsGenerator(
            listOf(),
            function42NoReturnCFG,
            function42NoReturn
        )
        val result = fdg.genCall(listOf())
        assertEquals(function42NoReturnCFG, result.callGraph)
        assertNull(result.result)
    }
}
