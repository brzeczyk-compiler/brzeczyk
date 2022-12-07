package compiler.intermediate_form

object Allocation {
    data class AllocationResult(
        val allocatedRegisters: Map<Register, Register>,
        val spilledRegisters: List<Register>,
    )

    fun allocateRegisters(
        linearProgram: List<Asmable>,
        livenessGraphs: Liveness.LivenessGraphs,
        accessibleRegisters: List<Register>,
    ): AllocationResult = TODO()
}
