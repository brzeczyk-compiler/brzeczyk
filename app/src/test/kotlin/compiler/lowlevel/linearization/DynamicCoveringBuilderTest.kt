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
    private val dummyNode1 = IFTNode.Dummy()
    private val dummyNode2 = IFTNode.Dummy()
    private val xorNode1 = IFTNode.BitXor(dummyNode1, dummyNode2)

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
        val covering = Pattern.Result(listOf(), 1) { _, _ -> listOf() }
        val pattern = matchNodesToPattern({ it is IFTNode.Dummy }, covering)
        val coveringBuilder = DynamicCoveringBuilder(instructionSetOf(pattern))

        assertEquals(listOf(), coveringBuilder.coverUnconditional(dummyNode1))
        assertEquals(listOf(), coveringBuilder.coverConditional(dummyNode1, "label", true))
    }

    @Test fun `test builder fails when covering is impossible`() {
        val pattern = matchNodesToPattern({ true }, null)
        val coveringBuilder = DynamicCoveringBuilder(instructionSetOf(pattern))

        assertFails { coveringBuilder.coverUnconditional(dummyNode1) }
        assertFails { coveringBuilder.coverConditional(dummyNode1, "label", true) }
    }

    @Test fun `test builder covers node with multiple children`() {
        val ret1Covering = Pattern.Result(listOf(), 1) { _, _ -> listOf() }
        val ret2Covering = Pattern.Result(listOf(), 1) { _, _ -> listOf() }
        val ret3Covering = Pattern.Result(listOf(dummyNode1, dummyNode2), 1) { _, _ -> listOf() }

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == dummyNode1 }, ret1Covering),
                matchNodesToPattern({ it == dummyNode2 }, ret2Covering),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering)
            )
        )

        assertEquals(listOf(), coveringBuilder.coverUnconditional(xorNode1))
        assertEquals(listOf(), coveringBuilder.coverConditional(xorNode1, "label", true))
    }

    @Test fun `test instructions are ordered bottom-up`() {
        // not a data class, so equality will be done by reference - which is what we want
        val termInstruction1 = Instruction.TerminalInstruction.Dummy()
        val termInstruction2 = Instruction.TerminalInstruction.Dummy()
        val termInstruction3 = Instruction.TerminalInstruction.Dummy()
        val ret1Covering = Pattern.Result(listOf(), 1) { _, _ -> listOf(termInstruction1) }
        val ret2Covering = Pattern.Result(listOf(), 1) { _, _ -> listOf(termInstruction2) }
        val ret3Covering = Pattern.Result(listOf(dummyNode1, dummyNode2), 1) { _, _ -> listOf(termInstruction3) }

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == dummyNode1 }, ret1Covering),
                matchNodesToPattern({ it == dummyNode2 }, ret2Covering),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering)
            )
        )

        val expectedInstructions = listOf(termInstruction1, termInstruction2, termInstruction3)
        assertEquals(expectedInstructions, coveringBuilder.coverUnconditional(xorNode1))
        assertEquals(expectedInstructions, coveringBuilder.coverConditional(xorNode1, "label", true))
    }

    @Test fun `test builder chooses the cheapest covering`() {
        // not a data class, so equality will be done by reference - which is what we want
        val termInstruction1 = Instruction.TerminalInstruction.Dummy()
        val termInstruction2 = Instruction.TerminalInstruction.Dummy()
        val termInstruction3 = Instruction.TerminalInstruction.Dummy()
        val termInstruction4 = Instruction.TerminalInstruction.Dummy()
        val ret1Covering = Pattern.Result(listOf(), 100) { _, _ -> listOf(termInstruction1) }
        val ret2Covering = Pattern.Result(listOf(), 100) { _, _ -> listOf(termInstruction2) }
        val ret3Covering = Pattern.Result(listOf(dummyNode1, dummyNode2), 10) { _, _ -> listOf(termInstruction3) }
        val ret4Covering = Pattern.Result(listOf(), 100) { _, _ -> listOf(termInstruction4) }

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == dummyNode1 }, ret1Covering),
                matchNodesToPattern({ it == dummyNode2 }, ret2Covering),
                matchNodesToPattern({ it == xorNode1 }, ret4Covering),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering)
            )
        )

        val expectedInstructions = listOf(termInstruction4)
        assertEquals(expectedInstructions, coveringBuilder.coverUnconditional(xorNode1))
        assertEquals(expectedInstructions, coveringBuilder.coverConditional(xorNode1, "label", true))
    }

    @Test fun `test builder chooses the cheapest covering even when it is more granular`() {
        // not a data class, so equality will be done by reference - which is what we want
        val termInstruction1 = Instruction.TerminalInstruction.Dummy()
        val termInstruction2 = Instruction.TerminalInstruction.Dummy()
        val termInstruction3 = Instruction.TerminalInstruction.Dummy()
        val termInstruction4 = Instruction.TerminalInstruction.Dummy()
        val ret1Covering = Pattern.Result(listOf(), 1) { _, _ -> listOf(termInstruction1) }
        val ret2Covering = Pattern.Result(listOf(), 1) { _, _ -> listOf(termInstruction2) }
        val ret3Covering = Pattern.Result(listOf(dummyNode1, dummyNode2), 1) { _, _ -> listOf(termInstruction3) }
        val ret4Covering = Pattern.Result(listOf(), 1000) { _, _ -> listOf(termInstruction4) }

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == dummyNode1 }, ret1Covering),
                matchNodesToPattern({ it == dummyNode2 }, ret2Covering),
                matchNodesToPattern({ it == xorNode1 }, ret4Covering),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering)
            )
        )

        val expectedInstructions = listOf(termInstruction1, termInstruction2, termInstruction3)
        assertEquals(expectedInstructions, coveringBuilder.coverUnconditional(xorNode1))
        assertEquals(expectedInstructions, coveringBuilder.coverConditional(xorNode1, "label", true))
    }

    @Test fun `test input registers of parent are the output registers of children`() {
        var outRegisterChildLeft: Register? = null
        val matchResultLeftChild: Pattern.Result = Pattern.Result(listOf(), 1) { _, outRegister ->
            outRegisterChildLeft = outRegister
            listOf()
        }
        var outRegisterChildRight: Register? = null
        val matchResultRightChild: Pattern.Result = Pattern.Result(listOf(), 1) { _, outRegister ->
            outRegisterChildRight = outRegister
            listOf()
        }
        var inRegisterParentFirst: Register? = null
        var inRegisterParentSecond: Register? = null
        val matchResultParent: Pattern.Result = Pattern.Result(listOf(dummyNode1, dummyNode2), 1) { inRegisters, _ ->
            inRegisterParentFirst = inRegisters[0]
            inRegisterParentSecond = inRegisters[1]
            listOf()
        }

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it === dummyNode1 }, matchResultLeftChild),
                matchNodesToPattern({ it === dummyNode2 }, matchResultRightChild),
                matchNodesToPattern({ it === xorNode1 }, matchResultParent)
            )
        )

        coveringBuilder.coverUnconditional(xorNode1)
        assertEquals(outRegisterChildLeft, inRegisterParentFirst)
        assertEquals(outRegisterChildRight, inRegisterParentSecond)
    }
}
