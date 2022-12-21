package compiler.lowlevel.allocation

import compiler.intermediate.Register
import compiler.lowlevel.dataflow.Liveness

object ColoringAllocation : Allocation {

    override fun allocateRegisters(
        livenessGraphs: Liveness.LivenessGraphs,
        accessibleRegisters: List<Register>,
    ): Allocation.AllocationResult = GraphColoring.color(livenessGraphs, accessibleRegisters)
}
