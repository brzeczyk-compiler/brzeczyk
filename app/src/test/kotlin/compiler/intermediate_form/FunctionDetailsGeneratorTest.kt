package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.reference_collections.referenceMapOf
import io.mockk.every
import io.mockk.spyk
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FunctionDetailsGeneratorTest {
    private val expressionToCfgMock = { expression: Expression, _: Variable? ->
        when (expression) {
            is Expression.NumberLiteral -> {
                val root = IntermediateFormTreeNode.Const(expression.value.toLong())
                ControlFlowGraphBuilder(root).build()
            }
            is Expression.BinaryOperation -> {
                val root = IntermediateFormTreeNode.GreaterThanOrEquals(
                    IntermediateFormTreeNode.RegisterRead(argRegister),
                    IntermediateFormTreeNode.Const(0L)
                )
                ControlFlowGraphBuilder(root).build()
            }
            is Expression.Variable -> {
                ControlFlowGraphBuilder(IntermediateFormTreeNode.RegisterRead(argRegister)).build()
            }
            else -> throw RuntimeException("Incorrect expression type")
        }
    }

    private val function42NoReturn = Function(
        "f42",
        listOf(),
        Type.Unit,
        listOf(
            Statement.Evaluation(Expression.NumberLiteral(42))
        )
    ) // czynność f42() { 42; }
    private val function42NoReturnCFG = ControlFlow.createGraphForEachFunction(
        Program(listOf(Program.Global.FunctionDefinition(function42NoReturn))),
        expressionToCfgMock,
        referenceMapOf(),
        referenceMapOf(),
        {}
    )[function42NoReturn]!!

    @Test
    fun `test genCall for zero argument, unit returning function`() {
        val fdg = FunctionDetailsGenerator(
            listOf(),
            function42NoReturnCFG,
            function42NoReturn,
            0u,
            mapOf(),
            0u
        )
        val result = fdg.genCall(listOf())
        assertEquals(function42NoReturnCFG, result.callGraph)
        assertNull(result.result)
    }

    private val functionReturn42 = Function(
        "return42",
        listOf(),
        Type.Number,
        listOf(
            Statement.FunctionReturn(Expression.NumberLiteral(42))
        )
    ) // czynność return42() { zwróć 42; }
    private val functionReturn42CFG = ControlFlow.createGraphForEachFunction(
        Program(listOf(Program.Global.FunctionDefinition(functionReturn42))),
        expressionToCfgMock,
        referenceMapOf(),
        referenceMapOf(),
        {}
    )[functionReturn42]!!

    @Test
    fun `test genCall for zero argument, non unit returning function`() {
        val fdg = FunctionDetailsGenerator(
            listOf(),
            functionReturn42CFG,
            functionReturn42,
            0u,
            mapOf(),
            0u
        )
        val result = fdg.genCall(listOf())

        val root = result.callGraph.entryTreeRoot as IntermediateFormTreeNode.RegisterWrite // should not fail
        val subNode = root.node as IntermediateFormTreeNode.Const // should not fail
        val resultNode = result.result as IntermediateFormTreeNode.RegisterRead // should not fail

        assertEquals(listOf(root), result.callGraph.treeRoots)
        assertEquals(42, subNode.value)

        assertEquals(FUNCTION_RESULT_REGISTER, resultNode.register)
        assertEquals(FUNCTION_RESULT_REGISTER, root.register)
    }

    private val maxWith0FunctionParam = Function.Parameter("x", Type.Number, null)
    private val maxWith0Function = Function(
        "maxWithZero",
        listOf(maxWith0FunctionParam),
        Type.Number,
        listOf(
            Statement.Conditional(
                Expression.BinaryOperation(
                    Expression.BinaryOperation.Kind.GREATER_THAN_OR_EQUALS,
                    Expression.Variable("x"),
                    Expression.NumberLiteral(0)
                ),
                listOf(Statement.FunctionReturn(Expression.Variable("x"))),
                listOf(Statement.FunctionReturn(Expression.NumberLiteral(0)))
            )
        )
    ) // czynność maxWithZero(x: Liczba) { jeżeli(x >= 0) zwróć x; wpp zwróć 0; }

    private val argRegister = Register()
    private val maxWith0CFG = ControlFlow.createGraphForEachFunction(
        Program(listOf(Program.Global.FunctionDefinition(maxWith0Function))),
        expressionToCfgMock,
        referenceMapOf("x" to maxWith0FunctionParam),
        referenceMapOf(),
        {}
    )[maxWith0Function]!!

    @Test
    fun `test genCall for function with arguments`() {
        val passedValue = IntermediateFormTreeNode.Const(3L)
        val passedVariable = Variable(Variable.Kind.VALUE, "x", Type.Number, null)

        val mockedFDG = spyk(
            FunctionDetailsGenerator(
                listOf(passedVariable),
                maxWith0CFG,
                maxWith0Function,
                0u,
                mapOf(),
                0u
            )
        )
        val mockedWriteIFT = IntermediateFormTreeNode.NoOp()
        every { mockedFDG.genWrite(passedVariable, any(), any()) } returns mockedWriteIFT

        val result = mockedFDG.genCall(listOf(passedValue))
        val cfg = result.callGraph

        assertEquals(result.result, IntermediateFormTreeNode.RegisterRead(FUNCTION_RESULT_REGISTER))

        // first instruction should write parameter value to appropriate place
        assertEquals(mockedWriteIFT, cfg.entryTreeRoot)

        // second instruction should be comparing values
        val secondNode = cfg.unconditionalLinks[mockedWriteIFT]
        assert(secondNode!! is IntermediateFormTreeNode.GreaterThanOrEquals)

        // if true, we should read value from arg Variable's location and store it in FUNCTION_RESULT_REGISTER
        val conditionalTrueNode = cfg.conditionalTrueLinks[secondNode]!! as IntermediateFormTreeNode.RegisterWrite
        assertEquals(FUNCTION_RESULT_REGISTER, conditionalTrueNode.register)
        assertEquals(argRegister, (conditionalTrueNode.node as IntermediateFormTreeNode.RegisterRead).register)

        // if false, we should store 0 in FUNCTION_RESULT_REGISTER, as function describes
        val conditionalFalseNode = cfg.conditionalFalseLinks[secondNode]!! as IntermediateFormTreeNode.RegisterWrite
        assertEquals(FUNCTION_RESULT_REGISTER, conditionalFalseNode.register)
        assertEquals(0L, (conditionalFalseNode.node as IntermediateFormTreeNode.Const).value)
    }
}
