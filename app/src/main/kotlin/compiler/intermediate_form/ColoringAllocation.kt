package compiler.intermediate_form

import compiler.common.intermediate_form.Allocation
import compiler.intermediate_form.allocation_graph_coloring.GraphColoring

object ColoringAllocation : Allocation {

    override fun allocateRegisters(
        livenessGraphs: Liveness.LivenessGraphs,
        accessibleRegisters: List<Register>,
    ): Allocation.AllocationResult = GraphColoring.color(livenessGraphs, accessibleRegisters)
}
