package compiler.intermediate.generators

import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import compiler.intermediate.assertHasSameStructureAs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForeignGeneratorDetailsGeneratorTest {
    private val initLabel = IFTNode.MemoryLabel("generatorNameInit")
    private val resumeLabel = IFTNode.MemoryLabel("generatorNameResume")
    private val finalizeLabel = IFTNode.MemoryLabel("generatorNameFinalize")

    private val argumentNode1 = IFTNode.Dummy()
    private val argumentNode2 = IFTNode.Dummy()

    private val callerSavedRegisters = listOf(Register.RAX, Register.RCX, Register.RDX, Register.RSI, Register.RDI, Register.R8, Register.R9, Register.R10, Register.R11)
    private val argumentPassingRegisters = listOf(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.R8, Register.R9)

    @Test
    fun `test genInitCall`() {
        val gdg = ForeignGeneratorDetailsGenerator(initLabel, resumeLabel, finalizeLabel)
        val args = (0..7).map { IFTNode.Const(it.toLong()) }.toList()

        val expectedCFGBuilder = ControlFlowGraphBuilder()
        for ((register, node) in argumentPassingRegisters zip args)
            expectedCFGBuilder.addSingleTree(IFTNode.RegisterWrite(register, node))
        expectedCFGBuilder.addSingleTree(IFTNode.StackPush(args[7]))
        expectedCFGBuilder.addSingleTree(IFTNode.StackPush(args[6]))
        expectedCFGBuilder.addSingleTree(IFTNode.Call(initLabel, argumentPassingRegisters, callerSavedRegisters))
        expectedCFGBuilder.addSingleTree(
            IFTNode.RegisterWrite(
                Register.RSP,
                IFTNode.Add(IFTNode.RegisterRead(Register.RSP), IFTNode.Const(16))
            )
        )

        val expectedCFG = expectedCFGBuilder.build()
        val expectedFirstResult = IFTNode.RegisterRead(Register.RAX)
        val result = gdg.genInitCall(args)
        expectedCFG assertHasSameStructureAs result.callGraph
        assertEquals(expectedFirstResult, result.result)
        assertNull(result.secondResult)
    }

    @Test
    fun `test genResumeCall`() {
        val gdg = ForeignGeneratorDetailsGenerator(initLabel, resumeLabel, finalizeLabel)

        val expectedCFGBuilder = ControlFlowGraphBuilder()
        expectedCFGBuilder.addSingleTree(IFTNode.RegisterWrite(Register.RDI, argumentNode1))
        expectedCFGBuilder.addSingleTree(IFTNode.RegisterWrite(Register.RSI, argumentNode2))
        expectedCFGBuilder.addSingleTree(
            IFTNode.Call(
                resumeLabel,
                listOf(Register.RDI, Register.RSI),
                callerSavedRegisters
            )
        )

        val expectedCFG = expectedCFGBuilder.build()
        val expectedFirstResult = IFTNode.RegisterRead(Register.RAX)
        val expectedSecondResult = IFTNode.RegisterRead(Register.RDX)
        val result = gdg.genResumeCall(argumentNode1, argumentNode2)
        expectedCFG assertHasSameStructureAs result.callGraph
        assertEquals(expectedFirstResult, result.result)
        assertEquals(expectedSecondResult, result.secondResult)
    }

    @Test
    fun `test genFinalizeCall`() {
        val gdg = ForeignGeneratorDetailsGenerator(initLabel, resumeLabel, finalizeLabel)

        val expectedCFGBuilder = ControlFlowGraphBuilder()
        expectedCFGBuilder.addSingleTree(IFTNode.RegisterWrite(Register.RDI, argumentNode1))
        expectedCFGBuilder.addSingleTree(
            IFTNode.Call(
                finalizeLabel,
                listOf(Register.RDI),
                callerSavedRegisters
            )
        )

        val expectedCFG = expectedCFGBuilder.build()
        val result = gdg.genFinalizeCall(argumentNode1)
        expectedCFG assertHasSameStructureAs result.callGraph
        assertNull(result.result)
        assertNull(result.secondResult)
    }
}
