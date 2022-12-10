package compiler.intermediate_form

import kotlin.test.Test
import kotlin.test.assertEquals

class LivenessTest {
    private val reg1 = Register()
    private val reg2 = Register()
    private val reg3 = Register()

    @Test
    fun `trivial program`() {
        val linearProgram = listOf(
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(),
            mapOf()
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `two registers defined but not used`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg2)),
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(), reg2 to setOf()),
            mapOf(reg1 to setOf(), reg2 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `two registers used but not defined`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg2)),
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(), reg2 to setOf()),
            mapOf(reg1 to setOf(), reg2 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `two registers not interfering each other`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg2)),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg2)),
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(), reg2 to setOf()),
            mapOf(reg1 to setOf(), reg2 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `two registers interfering each other`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg2)),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg2)),
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(reg2), reg2 to setOf(reg1)),
            mapOf(reg1 to setOf(), reg2 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `multiple registers defined at one`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1, reg2, reg3)),
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(reg2, reg3), reg2 to setOf(reg1, reg3), reg3 to setOf(reg1, reg2)),
            mapOf(reg1 to setOf(), reg2 to setOf(), reg3 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `multiple registers used at one`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1, reg2, reg3)),
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(), reg2 to setOf(), reg3 to setOf()),
            mapOf(reg1 to setOf(), reg2 to setOf(), reg3 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `instruction using and defining the same register`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1), regsDefined = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1)),
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf()),
            mapOf(reg1 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `instruction using and defining different registers`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1), regsDefined = listOf(reg2)),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg2)),
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(), reg2 to setOf()),
            mapOf(reg1 to setOf(), reg2 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `unconditional jumps`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1)),
            Instruction.UnconditionalJumpInstruction.Dummy("label2"),
            Label("label1"),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg2)),
            Instruction.RetInstruction.Dummy(),
            Label("label2"),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg2)),
            Instruction.UnconditionalJumpInstruction.Dummy("label1")
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(), reg2 to setOf()),
            mapOf(reg1 to setOf(), reg2 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `unconditional and conditional jumps`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1)),
            Instruction.ConditionalJumpInstruction.Dummy("label2"),
            Label("label1"),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg2)),
            Instruction.RetInstruction.Dummy(),
            Label("label2"),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg2)),
            Instruction.UnconditionalJumpInstruction.Dummy("label1")
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(reg2), reg2 to setOf(reg1)),
            mapOf(reg1 to setOf(), reg2 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `interference in loop`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1)),
            Label("loop_start"),
            Instruction.ConditionalJumpInstruction.Dummy("end_of_loop"),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1)),
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg2)),
            Instruction.UnconditionalJumpInstruction.Dummy("loop_start"),
            Label("end_of_loop"),
            Instruction.RetInstruction.Dummy(),
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(reg2), reg2 to setOf(reg1)),
            mapOf(reg1 to setOf(), reg2 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `copying is not interference`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1, reg3)),
            Instruction.InPlaceInstruction.MoveRR(reg2, reg1),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1, reg2, reg3)),
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(reg3), reg2 to setOf(reg3), reg3 to setOf(reg1, reg2)),
            mapOf(reg1 to setOf(reg2), reg2 to setOf(reg1), reg3 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `copying and interference`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1, reg2, reg3)),
            Instruction.InPlaceInstruction.MoveRR(reg2, reg1),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg1, reg2, reg3)),
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(reg2, reg3), reg2 to setOf(reg1, reg3), reg3 to setOf(reg1, reg2)),
            mapOf(reg1 to setOf(reg2), reg2 to setOf(reg1), reg3 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }

    @Test
    fun `multiple exit points`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(regsDefined = listOf(reg1)),
            Instruction.ConditionalJumpInstruction.Dummy("alternative_ending"),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg2)),
            Instruction.RetInstruction.Dummy(),
            Label("alternative_ending"),
            Instruction.InPlaceInstruction.Dummy(regsUsed = listOf(reg3)),
            Instruction.RetInstruction.Dummy()
        )
        val expectedLivenessGraphs = Liveness.LivenessGraphs(
            mapOf(reg1 to setOf(reg2, reg3), reg2 to setOf(reg1), reg3 to setOf(reg1)),
            mapOf(reg1 to setOf(), reg2 to setOf(), reg3 to setOf())
        )

        val livenessGraphs = Liveness.computeLiveness(linearProgram)
        assertEquals(expectedLivenessGraphs, livenessGraphs)
    }
}
