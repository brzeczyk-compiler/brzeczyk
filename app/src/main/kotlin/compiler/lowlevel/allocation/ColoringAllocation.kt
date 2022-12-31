package compiler.lowlevel.allocation

import compiler.intermediate.Register
import compiler.lowlevel.dataflow.Liveness

object ColoringAllocation : PartialAllocation {

    override fun allocateRegisters(
        livenessGraphs: Liveness.LivenessGraphs,
        selfAllocatedRegisters: List<Register>,
        availableRegisters: List<Register>,
    ): PartialAllocation.AllocationResult = GraphColoring.color(livenessGraphs, selfAllocatedRegisters, availableRegisters)
}
