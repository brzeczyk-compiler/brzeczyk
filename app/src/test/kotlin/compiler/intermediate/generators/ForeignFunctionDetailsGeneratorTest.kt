package compiler.intermediate.generators

import compiler.intermediate.CFGLinkType
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import compiler.intermediate.assertHasSameStructureAs
import kotlin.test.Test
import kotlin.test.assertEquals

class ForeignFunctionDetailsGeneratorTest {
    private val foreignLabel = IFTNode.MemoryLabel("FunctionName")

    private val callerSavedRegisters = listOf(Register.RAX, Register.RCX, Register.RDX, Register.RSI, Register.RDI, Register.R8, Register.R9, Register.R10, Register.R11)
    private val argumentPassingRegisters = listOf(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.R8, Register.R9)

    @Test
    fun `test genCall for zero argument, Unit function`() {
        val fdg = ForeignFunctionDetailsGenerator(foreignLabel, 0)
        val expected = ControlFlowGraphBuilder(IFTNode.Call(foreignLabel, emptyList(), callerSavedRegisters)).build()
        val result = fdg.genCall(listOf())

        expected assertHasSameStructureAs result.callGraph
        assertEquals(null, result.result)
    }

    @Test
    fun `test genCall for two argument, Unit return function`() {
        val fdg = ForeignFunctionDetailsGenerator(foreignLabel, 0)

        val arg1 = IFTNode.Dummy()
        val arg2 = IFTNode.Dummy()
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
        expectedCFGBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, IFTNode.Call(foreignLabel, argumentPassingRegisters.take(2), callerSavedRegisters))
        val expected = expectedCFGBuilder.build()

        expected assertHasSameStructureAs result.callGraph
        assertEquals(null, result.result)
    }

    @Test
    fun `test genCall for zero argument, Number returning function`() {
        val fdg = ForeignFunctionDetailsGenerator(foreignLabel, 1)

        val expectedCFGBuilder = ControlFlowGraphBuilder(IFTNode.Call(foreignLabel, emptyList(), callerSavedRegisters))

        val expectedResult = IFTNode.RegisterRead(Register.RAX)
        val expected = expectedCFGBuilder.build()
        val result = fdg.genCall(listOf())
        expected assertHasSameStructureAs result.callGraph
        assertEquals(expectedResult, result.result)
    }

    @Test
    fun `test genCall for zero argument, two Numbers returning function`() {
        val fdg = ForeignFunctionDetailsGenerator(foreignLabel, 2)

        val expectedCFGBuilder = ControlFlowGraphBuilder(IFTNode.Call(foreignLabel, emptyList(), callerSavedRegisters))

        val expectedResult = IFTNode.RegisterRead(Register.RAX)
        val expectedSecondResult = IFTNode.RegisterRead(Register.RDX)
        val expected = expectedCFGBuilder.build()
        val result = fdg.genCall(listOf())
        expected assertHasSameStructureAs result.callGraph
        assertEquals(expectedResult, result.result)
        assertEquals(expectedSecondResult, result.secondResult)
    }

    @Test
    fun `test genCall for 8 argument, Number return function`() {
        val fdg = ForeignFunctionDetailsGenerator(foreignLabel, 1)

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
        val callNode = IFTNode.Call(foreignLabel, argumentPassingRegisters, callerSavedRegisters)
        expectedCFGBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, callNode)

        // remove arguments previously put on stack
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.RegisterWrite(
                Register.RSP,
                IFTNode.Add(
                    IFTNode.RegisterRead(Register.RSP),
                    IFTNode.Const(16)
                )
            )
        )

        val expectedResult = IFTNode.RegisterRead(Register.RAX)
        val expected = expectedCFGBuilder.build()

        expected assertHasSameStructureAs result.callGraph
        assertEquals(expectedResult, result.result)
    }
}
