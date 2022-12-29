package compiler.lowlevel.linearization

import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import compiler.lowlevel.Instruction
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DynamicCoveringBuilderTest {
    private val noOpNode1 = IFTNode.NoOp()
    private val noOpNode2 = IFTNode.NoOp()
    private val xorNode1 = IFTNode.BitXor(noOpNode1, noOpNode2)

    private fun instructionSetOf(vararg patterns: Pattern): List<Pattern> {
        return patterns.asList()
    }

    private fun matchNodesToPattern(
        predicate: (IFTNode) -> Boolean,
        matchResult: Pattern.Result?,
    ): Pattern {
        val pattern = mockk<Pattern>()
        // mockk needs to know what to do in other cases
        every { pattern.matchValue(any()) } returns null
        every { pattern.matchConditional(any(), any(), any()) } returns null
        every { pattern.matchUnconditional(any()) } returns null

        every { pattern.matchValue(match(predicate)) } returns matchResult
        every { pattern.matchConditional(match(predicate), any(), any()) } returns matchResult
        every { pattern.matchUnconditional(match(predicate)) } returns matchResult
        return pattern
    }

    @Test fun `test basic`() {
        val covering = Pattern.Result(listOf(), 1, { _, _ -> listOf() })
        val pattern = matchNodesToPattern({ it is IFTNode.NoOp }, covering)
        val coveringBuilder = DynamicCoveringBuilder(instructionSetOf(pattern))

        assertEquals(listOf(), coveringBuilder.coverUnconditional(noOpNode1))
        assertEquals(listOf(), coveringBuilder.coverConditional(noOpNode1, "label", true))
    }

    @Test fun `test builder fails when covering is impossible`() {
        val pattern = matchNodesToPattern({ true }, null)
        val coveringBuilder = DynamicCoveringBuilder(instructionSetOf(pattern))

        assertFails { coveringBuilder.coverUnconditional(noOpNode1) }
        assertFails { coveringBuilder.coverConditional(noOpNode1, "label", true) }
    }

    @Test fun `test builder covers node with multiple children`() {
        val ret1Covering = Pattern.Result(listOf(), 1, { _, _ -> listOf() })
        val ret2Covering = Pattern.Result(listOf(), 1, { _, _ -> listOf() })
        val ret3Covering = Pattern.Result(listOf(noOpNode1, noOpNode2), 1, { _, _ -> listOf() })

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == noOpNode1 }, ret1Covering),
                matchNodesToPattern({ it == noOpNode2 }, ret2Covering),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering)
            )
        )

        assertEquals(listOf(), coveringBuilder.coverUnconditional(xorNode1))
        assertEquals(listOf(), coveringBuilder.coverConditional(xorNode1, "label", true))
    }

    @Test fun `test instructions are ordered bottom-up`() {
        // not a data class, so equality will be done by reference - which is what we want
        val retInstruction1 = Instruction.RetInstruction.Ret()
        val retInstruction2 = Instruction.RetInstruction.Ret()
        val retInstruction3 = Instruction.RetInstruction.Ret()
        val ret1Covering = Pattern.Result(listOf(), 1, { _, _ -> listOf(retInstruction1) })
        val ret2Covering = Pattern.Result(listOf(), 1, { _, _ -> listOf(retInstruction2) })
        val ret3Covering = Pattern.Result(listOf(noOpNode1, noOpNode2), 1, { _, _ -> listOf(retInstruction3) })

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == noOpNode1 }, ret1Covering),
                matchNodesToPattern({ it == noOpNode2 }, ret2Covering),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering)
            )
        )

        val expectedInstructions = listOf(retInstruction1, retInstruction2, retInstruction3)
        assertEquals(expectedInstructions, coveringBuilder.coverUnconditional(xorNode1))
        assertEquals(expectedInstructions, coveringBuilder.coverConditional(xorNode1, "label", true))
    }

    @Test fun `test builder chooses the cheapest covering`() {
        // not a data class, so equality will be done by reference - which is what we want
        val retInstruction1 = Instruction.RetInstruction.Ret()
        val retInstruction2 = Instruction.RetInstruction.Ret()
        val retInstruction3 = Instruction.RetInstruction.Ret()
        val retInstruction4 = Instruction.RetInstruction.Ret()
        val ret1Covering = Pattern.Result(listOf(), 100, { _, _ -> listOf(retInstruction1) })
        val ret2Covering = Pattern.Result(listOf(), 100, { _, _ -> listOf(retInstruction2) })
        val ret3Covering = Pattern.Result(listOf(noOpNode1, noOpNode2), 10, { _, _ -> listOf(retInstruction3) })
        val ret4Covering = Pattern.Result(listOf(), 100, { _, _ -> listOf(retInstruction4) })

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == noOpNode1 }, ret1Covering),
                matchNodesToPattern({ it == noOpNode2 }, ret2Covering),
                matchNodesToPattern({ it == xorNode1 }, ret4Covering),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering)
            )
        )

        val expectedInstructions = listOf(retInstruction4)
        assertEquals(expectedInstructions, coveringBuilder.coverUnconditional(xorNode1))
        assertEquals(expectedInstructions, coveringBuilder.coverConditional(xorNode1, "label", true))
    }

    @Test fun `test builder chooses the cheapest covering even when it is more granular`() {
        // not a data class, so equality will be done by reference - which is what we want
        val retInstruction1 = Instruction.RetInstruction.Ret()
        val retInstruction2 = Instruction.RetInstruction.Ret()
        val retInstruction3 = Instruction.RetInstruction.Ret()
        val retInstruction4 = Instruction.RetInstruction.Ret()
        val ret1Covering = Pattern.Result(listOf(), 1, { _, _ -> listOf(retInstruction1) })
        val ret2Covering = Pattern.Result(listOf(), 1, { _, _ -> listOf(retInstruction2) })
        val ret3Covering = Pattern.Result(listOf(noOpNode1, noOpNode2), 1, { _, _ -> listOf(retInstruction3) })
        val ret4Covering = Pattern.Result(listOf(), 1000, { _, _ -> listOf(retInstruction4) })

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == noOpNode1 }, ret1Covering),
                matchNodesToPattern({ it == noOpNode2 }, ret2Covering),
                matchNodesToPattern({ it == xorNode1 }, ret4Covering),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering)
            )
        )

        val expectedInstructions = listOf(retInstruction1, retInstruction2, retInstruction3)
        assertEquals(expectedInstructions, coveringBuilder.coverUnconditional(xorNode1))
        assertEquals(expectedInstructions, coveringBuilder.coverConditional(xorNode1, "label", true))
    }

    @Test fun `test input registers of parent are the output registers of children`() {
        var outRegisterChildLeft: Register? = null
        val matchResultLeftChild: Pattern.Result = Pattern.Result(listOf(), 1, { _, outRegister ->
            outRegisterChildLeft = outRegister
            listOf()
        })
        var outRegisterChildRight: Register? = null
        val matchResultRightChild: Pattern.Result = Pattern.Result(listOf(), 1, { _, outRegister ->
            outRegisterChildRight = outRegister
            listOf()
        })
        var inRegisterParentFirst: Register? = null
        var inRegisterParentSecond: Register? = null
        val matchResultParent: Pattern.Result = Pattern.Result(listOf(noOpNode1, noOpNode2), 1, { inRegisters, _ ->
            inRegisterParentFirst = inRegisters[0]
            inRegisterParentSecond = inRegisters[1]
            listOf()
        })

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == noOpNode1 }, matchResultLeftChild),
                matchNodesToPattern({ it == noOpNode2 }, matchResultRightChild),
                matchNodesToPattern({ it == xorNode1 }, matchResultParent)
            )
        )

        coveringBuilder.coverUnconditional(xorNode1)
        assertEquals(outRegisterChildLeft, inRegisterParentFirst)
        assertEquals(outRegisterChildRight, inRegisterParentSecond)
    }
}
