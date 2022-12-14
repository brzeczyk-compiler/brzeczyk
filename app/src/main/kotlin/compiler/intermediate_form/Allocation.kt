package compiler.intermediate_form

import compiler.intermediate_form.allocation_graph_coloring.GraphColoring

object Allocation {
    data class AllocationResult(
        val allocatedRegisters: Map<Register, Register>,
        val spilledRegisters: List<Register>,
    )

    fun allocateRegisters(
        linearProgram: List<Asmable>,
        livenessGraphs: Liveness.LivenessGraphs,
        accessibleRegisters: List<Register>,
    ): AllocationResult = GraphColoring.color(livenessGraphs, accessibleRegisters)
}
