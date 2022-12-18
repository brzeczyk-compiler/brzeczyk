package compiler.intermediate_form

import compiler.common.intermediate_form.Allocation
import kotlin.test.Test
import kotlin.test.assertEquals

class SpillsHandlingAllocationTest {

    @Test
    fun `test program which uses no registers`() {
        val linearProgram = listOf<Asmable>(
            Instruction.InPlaceInstruction.Dummy(),
            Instruction.RetInstruction.Dummy(),
            Instruction.UnconditionalJumpInstruction.Dummy("targetLabel"),
            Instruction.ConditionalJumpInstruction.Dummy("targetLabel"),
            Label("targetLabel"),
        )
        val livenessGraphs = Liveness.LivenessGraphs(mapOf(), mapOf())
        val orderedPhysicalRegisters = listOf<Register>()
        val allocator = object : Allocation {
            override fun allocateRegisters(
                livenessGraphs: Liveness.LivenessGraphs,
                accessibleRegisters: List<Register>
            ): Allocation.AllocationResult = Allocation.AllocationResult(mapOf(), listOf())
        }

        val result = SpillsHandlingAllocation.allocateRegistersWithSpillsHandling(
            linearProgram,
            livenessGraphs,
            orderedPhysicalRegisters,
            allocator,
        )

        assertEquals(mapOf(), result.allocatedRegisters)
        assertEquals(linearProgram, result.linearProgram)
        assertEquals(0u, result.spilledOffset)
    }
}