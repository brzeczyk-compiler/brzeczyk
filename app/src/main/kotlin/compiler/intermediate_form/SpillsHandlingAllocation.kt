package compiler.intermediate_form



object SpillsHandlingAllocation {

    data class Result(
        val allocatedRegisters: Map<Register, Register>,
        val linearProgram: List<Asmable>,
        val spilledOffset: ULong,
    )

    // Registers should be sorted by likeliness to be reserved for spilled
    fun allocateRegistersWithSpillsHandling(
        linearProgram: List<Asmable>,
        livenessGraphs: Liveness.LivenessGraphs,
        orderedPhysicalRegisters: List<Register>,
    ): Result {
        var reservedRegsNumber = 0
        while (true) {
            val availableRegisters = orderedPhysicalRegisters.drop(reservedRegsNumber)
            val allocationResult = Allocation.allocateRegisters(livenessGraphs, availableRegisters)
            val spilledRegisters = allocationResult.spilledRegisters.toSet()

            val instructionsToSpilled: Map<Int, SpilledInstruction> = linearProgram
                .filterIsInstance<Instruction>()
                .associate { linearProgram.indexOf(it) to toSpilledInstruction(it, spilledRegisters) }
            val maxInstructionSpill = instructionsToSpilled.values.maxOfOrNull { it.numberOfSpilledRegs } ?: 0

            if (maxInstructionSpill <= reservedRegsNumber) {

                val spilledRegistersColoring: Map<Register, ULong> = colorSpilledRegisters(
                    spilledRegisters,
                    livenessGraphs,
                )

                val registerAllocation: Map<Register, Register> = allocateSpilledRegisters(
                    orderedPhysicalRegisters.take(reservedRegsNumber),
                    allocationResult.allocatedRegisters,
                    instructionsToSpilled.values.toList(),
                )

                val newLinearProgram: List<Asmable> = transformProgram(
                    linearProgram,
                    instructionsToSpilled,
                    registerAllocation,
                    spilledRegistersColoring,
                )

                return Result(
                    registerAllocation,
                    newLinearProgram,
                    (spilledRegistersColoring.values.maxOrNull() ?: 0) as ULong,
                )
            }

            reservedRegsNumber ++
        }
    }

    data class SpilledInstruction(
        val instruction: Instruction,
        val spilledUsedRegs: Set<Register>,
        val spilledDefinedRegs: Set<Register>,
        val numberOfSpilledRegs: Int = (spilledUsedRegs + spilledDefinedRegs).size,
        val isSpilled: Boolean = (numberOfSpilledRegs > 0),
    )

    private fun toSpilledInstruction(
        instruction: Instruction,
        spilledRegisters: Set<Register>,
    ): SpilledInstruction = SpilledInstruction(
        instruction,
        spilledRegisters.intersect(instruction.regsUsed.toSet()),
        spilledRegisters.intersect(instruction.regsDefined.toSet()),
    )

    private fun colorSpilledRegisters(
        spilledRegisters: Set<Register>,
        livenessGraphs: Liveness.LivenessGraphs,
    ): Map<Register, ULong> {
        val spillsPossibleColors = spilledRegisters.map { Register() }
        val spillsColoring: Map<Register, Register> = Allocation.allocateRegisters(
            Liveness.LivenessGraphs(
                Liveness.inducedSubgraph(livenessGraphs.interferenceGraph, spilledRegisters),
                mapOf(),
            ),
            spillsPossibleColors,
        ).allocatedRegisters
        val spillsColors = spillsPossibleColors.filter { it in spillsColoring.values }
        return spillsColoring.entries.associate { it.key to (spillsColors.indexOf(it.value) + 1).toULong() }
    }

    private fun allocateSpilledRegisters(
        reservedRegisters: List<Register>,
        partialAllocation: Map<Register, Register>,
        spilledInstructions: List<SpilledInstruction>,
    ): Map<Register, Register> {
        val newAllocation = partialAllocation.toMutableMap()
        spilledInstructions
            .filter { it.isSpilled }
            .map { it.spilledDefinedRegs + it.spilledUsedRegs }
            .forEach { spilledRegs -> spilledRegs.withIndex().forEach { newAllocation[it.value] = reservedRegisters[it.index] } }
        return newAllocation
    }

    private fun transformProgram(
        linearProgram: List<Asmable>,
        instructionsToSpilled: Map<Int, SpilledInstruction>,
        allocatedRegisters: Map<Register, Register>,
        spillsColoring: Map<Register, ULong>,
    ): List<Asmable> {
        val newLinearProgram = linearProgram.toMutableList()

        fun handleSpilledInstruction(spilledInstruction: SpilledInstruction) {
            spilledInstruction.spilledUsedRegs.forEach {
                newLinearProgram.add(Instruction.InPlaceInstruction.MoveRM(
                    allocatedRegisters[it]!!,
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(spillsColoring[it]!! * memoryUnitSize)),
                ))
            }

            newLinearProgram.add(spilledInstruction.instruction)

            spilledInstruction.spilledDefinedRegs.forEach {
                newLinearProgram.add(Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(spillsColoring[it]!! * memoryUnitSize)),
                    allocatedRegisters[it]!!,
                ))
            }
        }

        linearProgram
            .withIndex()
            .map { instructionsToSpilled[it.index] ?: it.value }
            .forEach {
                if (it is Label) newLinearProgram.add(it)
                if (it is SpilledInstruction) handleSpilledInstruction(it)
            }

        return newLinearProgram
    }

}
