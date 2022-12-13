package compiler.intermediate_form

import compiler.common.intermediate_form.FunctionDetailsGenerator

object SpillsHandlingAllocation {

    fun allocateRegistersWithSpillsHandling(
        livenessGraphs: Liveness.LivenessGraphs,
        fdg: FunctionDetailsGenerator
    ): Allocation.AllocationResult {
        // Try allocating registers from H - R, and then handle spills with R (for |R| = 0, 1, 2, ...)
        // + communicate with fdg to set additional offset for spilled registers.
        return TODO()
    }
}
