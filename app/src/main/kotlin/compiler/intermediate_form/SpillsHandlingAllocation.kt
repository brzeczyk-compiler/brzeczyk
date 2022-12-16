package compiler.intermediate_form

object SpillsHandlingAllocation {

    data class AllocationResultWithSpilledOffset(
        val allocatedRegisters: Map<Register, Register>,
        val linearProgram: List<Asmable>,
        val spilledOffset: ULong
    )

    fun allocateRegistersWithSpillsHandling(
        linearProgram: List<Asmable>,
        livenessGraphs: Liveness.LivenessGraphs,
    ): Allocation.AllocationResult {
        // Try allocating registers from H - R, and then handle spills with R (for |R| = 0, 1, 2, ...)
        // + compute offset needed to allocate on stack for spilled registers
        // + create linear program with definitions and usages inserted in appropriate places
        return TODO()
    }
}
