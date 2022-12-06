package compiler.intermediate_form

object Allocation {
    data class AllocationResult(
        val allocatedRegisters: Map<Register, Register>,
        val spilledRegisters: List<Register>,
    )

    fun allocateRegisters(
        linearProgram: List<AsmAble>,
        livelinessGraphs: Liveliness.LivelinessGraphs,
        accessibleRegisters: List<Register>,
    ): AllocationResult = TODO()
}
