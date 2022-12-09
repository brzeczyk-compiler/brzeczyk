package compiler.intermediate_form

import compiler.common.intermediate_form.DynamicCoveringBuilder
import compiler.common.intermediate_form.MatchResult
import compiler.intermediate_form.InstructionSet.InstructionPattern
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class DynamicCoveringBuilderTest {
    private val noOpNode1 = IntermediateFormTreeNode.NoOp()
    private val noOpNode2 = IntermediateFormTreeNode.NoOp()
    private val xorNode1 = IntermediateFormTreeNode.BitXor(noOpNode1, noOpNode2)

    private fun coveringWithChildrenAndInstructions(
        children: List<IntermediateFormTreeNode>,
        instructions: List<Instruction>
    ): MatchResult {
        return Pair(children) { _, _ -> instructions }
    }

    private fun instructionSetOf(vararg patterns: InstructionPattern): InstructionSet {
        val instructionSet = mockk<InstructionSet>()
        every { instructionSet.getInstructionSet() } returns patterns.asList()
        return instructionSet
    }

    private fun matchNodesToPattern(
        predicate: (IntermediateFormTreeNode) -> Boolean,
        matchResult: MatchResult,
        cost: Int
    ): InstructionPattern {
        val pattern = mockk<InstructionPattern>()
        // mockk needs to know what to do in other cases
        every { pattern.matchValue(any()) } returns null
        every { pattern.matchConditional(any(), any(), any()) } returns null
        every { pattern.matchUnconditional(any()) } returns null

        every { pattern.matchValue(match(predicate)) } returns matchResult
        every { pattern.matchConditional(match(predicate), any(), any()) } returns matchResult
        every { pattern.matchUnconditional(match(predicate)) } returns matchResult
        every { pattern.getCost() } returns cost
        return pattern
    }

    @Test fun `test basic`() {
        val covering = coveringWithChildrenAndInstructions(listOf(), listOf())
        val pattern = matchNodesToPattern({ it is IntermediateFormTreeNode.NoOp }, covering, 1)
        val coveringBuilder = DynamicCoveringBuilder(instructionSetOf(pattern))

        assertEquals(listOf(), coveringBuilder.coverUnconditional(noOpNode1))
        assertEquals(listOf(), coveringBuilder.coverConditional(noOpNode1, "label", true))
    }

    @Test fun `test builder fails when covering is impossible`() {
        val pattern = matchNodesToPattern({ true }, null, 1)
        val coveringBuilder = DynamicCoveringBuilder(instructionSetOf(pattern))

        assertFails { coveringBuilder.coverUnconditional(noOpNode1) }
        assertFails { coveringBuilder.coverConditional(noOpNode1, "label", true) }
    }

    @Test fun `test builder covers node with multiple children`() {
        val ret1Covering = coveringWithChildrenAndInstructions(listOf(), listOf())
        val ret2Covering = coveringWithChildrenAndInstructions(listOf(), listOf())
        val ret3Covering = coveringWithChildrenAndInstructions(listOf(noOpNode1, noOpNode2), listOf())

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == noOpNode1 }, ret1Covering, 1),
                matchNodesToPattern({ it == noOpNode2 }, ret2Covering, 1),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering, 1)
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
        val ret1Covering = coveringWithChildrenAndInstructions(listOf(), listOf(retInstruction1))
        val ret2Covering = coveringWithChildrenAndInstructions(listOf(), listOf(retInstruction2))
        val ret3Covering = coveringWithChildrenAndInstructions(listOf(noOpNode1, noOpNode2), listOf(retInstruction3))

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == noOpNode1 }, ret1Covering, 1),
                matchNodesToPattern({ it == noOpNode2 }, ret2Covering, 1),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering, 1)
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
        val ret1Covering = coveringWithChildrenAndInstructions(listOf(), listOf(retInstruction1))
        val ret2Covering = coveringWithChildrenAndInstructions(listOf(), listOf(retInstruction2))
        val ret3Covering = coveringWithChildrenAndInstructions(listOf(noOpNode1, noOpNode2), listOf(retInstruction3))
        val ret4Covering = coveringWithChildrenAndInstructions(listOf(), listOf(retInstruction4))

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == noOpNode1 }, ret1Covering, 100),
                matchNodesToPattern({ it == noOpNode2 }, ret2Covering, 100),
                matchNodesToPattern({ it == xorNode1 }, ret4Covering, 10),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering, 100)
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
        val ret1Covering = coveringWithChildrenAndInstructions(listOf(), listOf(retInstruction1))
        val ret2Covering = coveringWithChildrenAndInstructions(listOf(), listOf(retInstruction2))
        val ret3Covering = coveringWithChildrenAndInstructions(listOf(noOpNode1, noOpNode2), listOf(retInstruction3))
        val ret4Covering = coveringWithChildrenAndInstructions(listOf(), listOf(retInstruction4))

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == noOpNode1 }, ret1Covering, 1),
                matchNodesToPattern({ it == noOpNode2 }, ret2Covering, 1),
                matchNodesToPattern({ it == xorNode1 }, ret4Covering, 1000),
                matchNodesToPattern({ it == xorNode1 }, ret3Covering, 1)
            )
        )

        val expectedInstructions = listOf(retInstruction1, retInstruction2, retInstruction3)
        assertEquals(expectedInstructions, coveringBuilder.coverUnconditional(xorNode1))
        assertEquals(expectedInstructions, coveringBuilder.coverConditional(xorNode1, "label", true))
    }

    @Test fun `test input registers of parent are the output registers of children`() {
        var outRegisterChildLeft: Register? = null
        val matchResultLeftChild: MatchResult = Pair(listOf()) { _, outRegister ->
            outRegisterChildLeft = outRegister
            listOf()
        }
        var outRegisterChildRight: Register? = null
        val matchResultRightChild: MatchResult = Pair(listOf()) { _, outRegister ->
            outRegisterChildRight = outRegister
            listOf()
        }
        var inRegisterParentFirst: Register? = null
        var inRegisterParentSecond: Register? = null
        val matchResultParent: MatchResult = Pair(listOf(noOpNode1, noOpNode2)) { inRegisters, _ ->
            inRegisterParentFirst = inRegisters[0]
            inRegisterParentSecond = inRegisters[1]
            listOf()
        }

        val coveringBuilder = DynamicCoveringBuilder(
            instructionSetOf(
                matchNodesToPattern({ it == noOpNode1 }, matchResultLeftChild, 1),
                matchNodesToPattern({ it == noOpNode2 }, matchResultRightChild, 1),
                matchNodesToPattern({ it == xorNode1 }, matchResultParent, 1)
            )
        )

        coveringBuilder.coverUnconditional(xorNode1)
        assertEquals(outRegisterChildLeft, inRegisterParentFirst)
        assertEquals(outRegisterChildRight, inRegisterParentSecond)
    }
}
