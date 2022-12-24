package compiler.intermediate.generators

import compiler.intermediate.CFGLinkType
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import kotlin.test.Test
import kotlin.test.assertEquals

class ForeignFunctionDetailsGeneratorTest {
    private val foreignLabel = IFTNode.MemoryLabel("FunctionName")

    @Test
    fun `test genCall for zero argument, Unit function`() {
        val fdg = ForeignFunctionDetailsGenerator(foreignLabel, false)
        val expected = ControlFlowGraphBuilder(IFTNode.Call(foreignLabel)).build()
        val result = fdg.genCall(listOf())

        assert(expected.equalsByValue(result.callGraph))
        assertEquals(null, result.result)
    }

    @Test
    fun `test genCall for two argument, Unit return function`() {
        val fdg = ForeignFunctionDetailsGenerator(foreignLabel, false)

        val arg1 = IFTNode.NoOp()
        val arg2 = IFTNode.NoOp()
        val result = fdg.genCall(listOf(arg1, arg2))

        val expectedCFGBuilder = ControlFlowGraphBuilder()
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.RegisterWrite(Register.RDI, arg1)
        )
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.RegisterWrite(Register.RSI, arg2)
        )
        expectedCFGBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, IFTNode.Call(foreignLabel))
        val expected = expectedCFGBuilder.build()

        assert(expected.equalsByValue(result.callGraph))
        assertEquals(null, result.result)
    }

    @Test
    fun `test genCall for zero argument, Number returning function`() {
        val fdg = ForeignFunctionDetailsGenerator(foreignLabel, true)

        val expectedCFGBuilder = ControlFlowGraphBuilder(IFTNode.Call(foreignLabel))

        val expectedResult = IFTNode.RegisterRead(Register.RAX)
        val expected = expectedCFGBuilder.build()
        val result = fdg.genCall(listOf())
        assert(expected.equalsByValue(result.callGraph))
        assertEquals(expectedResult, result.result)
    }

    @Test
    fun `test genCall for 8 argument, Number return function`() {
        val fdg = ForeignFunctionDetailsGenerator(foreignLabel, true)

        val args = (0..7).map { IFTNode.Const(it.toLong()) }.toList()
        val result = fdg.genCall(args)

        val expectedCFGBuilder = ControlFlowGraphBuilder()

        // write args
        for ((argRegister, arg) in argPositionToRegister zip args) {
            expectedCFGBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(argRegister, arg)
            )
        }
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.StackPush(args[7])
        )
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.StackPush(args[6])
        )
        // call
        expectedCFGBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, IFTNode.Call(foreignLabel))

        // remove arguments previously put on stack
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.Add(
                IFTNode.RegisterRead(Register.RSP),
                IFTNode.Const(16)
            )
        )

        val expectedResult = IFTNode.RegisterRead(Register.RAX)
        val expected = expectedCFGBuilder.build()

        assert(expected.equalsByValue(result.callGraph))
        assertEquals(expectedResult, result.result)
    }
}
