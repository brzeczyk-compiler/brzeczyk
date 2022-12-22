package compiler.lowlevel.allocation

import compiler.intermediate.Register
import compiler.lowlevel.dataflow.Liveness

interface PartialAllocation {
    data class AllocationResult(
        val allocatedRegisters: Map<Register, Register>,
        val spilledRegisters: List<Register>,
    )

    fun allocateRegisters(
        livenessGraphs: Liveness.LivenessGraphs,
        accessibleRegisters: List<Register>,
    ): AllocationResult
}
