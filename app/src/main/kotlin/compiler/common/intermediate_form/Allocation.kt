package compiler.common.intermediate_form

import compiler.intermediate_form.Liveness
import compiler.intermediate_form.Register

interface Allocation {
    data class AllocationResult(
        val allocatedRegisters: Map<Register, Register>,
        val spilledRegisters: List<Register>,
    )

    fun allocateRegisters(
        livenessGraphs: Liveness.LivenessGraphs,
        accessibleRegisters: List<Register>,
    ): AllocationResult
}