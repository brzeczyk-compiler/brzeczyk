package compiler.lowlevel.allocation

import compiler.intermediate.Register
import compiler.intermediate.generators.memoryUnitSize
import compiler.lowlevel.Addressing
import compiler.lowlevel.Asmable
import compiler.lowlevel.Instruction
import compiler.lowlevel.Label
import compiler.lowlevel.dataflow.Liveness

object Allocation {

    val REGISTER_ORDER = listOf(
        Register.R11,
        Register.R10,
        Register.R9,
        Register.R8,
        Register.RDI,
        Register.RSI,
        Register.RDX,
        Register.RCX,
        Register.RBX,
        Register.RAX,
        Register.R15,
        Register.R14,
        Register.R13,
        Register.R12,
        Register.RBP,
        Register.RSP,
    )

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
        allocator: PartialAllocation
    ): Result {
        var reservedRegistersNumber = 0
        while (true) {
            val availableRegisters = orderedPhysicalRegisters.drop(reservedRegistersNumber)
            val allocationResult = allocator.allocateRegisters(livenessGraphs, availableRegisters)
            val spilledRegisters = allocationResult.spilledRegisters.toSet()

            val instructionsToSpilled: Map<Int, SpilledInstruction> = linearProgram
                .filterIsInstance<Instruction>()
                .associate { linearProgram.indexOf(it) to toSpilledInstruction(it, spilledRegisters) }
            val maxInstructionSpill = instructionsToSpilled.values.maxOfOrNull { it.numberOfSpilledRegisters } ?: 0

            if (maxInstructionSpill <= reservedRegistersNumber) {

                val spilledRegistersColoring: Map<Register, ULong> = colorSpilledRegisters(
                    spilledRegisters,
                    livenessGraphs,
                    allocator,
                )

                val registerAllocation: Map<Register, Register> = allocateSpilledRegisters(
                    orderedPhysicalRegisters.take(reservedRegistersNumber),
                    allocationResult.allocatedRegisters,
                    instructionsToSpilled.values.toList(),
                )

                val newLinearProgram: List<Asmable> = transformProgram(
                    linearProgram,
                    instructionsToSpilled,
                    registerAllocation,
                    spilledRegistersColoring,
                ).filterNot {
                    it is Instruction.InPlaceInstruction.MoveRR &&
                        registerAllocation[it.reg_dest] == registerAllocation[it.reg_src]
                }

                return Result(
                    registerAllocation,
                    newLinearProgram,
                    (spilledRegistersColoring.values.maxOrNull() ?: 0u) * memoryUnitSize,
                )
            }

            reservedRegistersNumber ++
        }
    }

    data class SpilledInstruction(
        val instruction: Instruction,
        val spilledUsedRegisters: Set<Register>,
        val spilledDefinedRegisters: Set<Register>,
        val numberOfSpilledRegisters: Int = (spilledUsedRegisters + spilledDefinedRegisters).size,
        val isSpilled: Boolean = (numberOfSpilledRegisters > 0),
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
        allocator: PartialAllocation,
    ): Map<Register, ULong> {
        val spillsPossibleColors = spilledRegisters.map { Register() }
        val spillsColoring: Map<Register, Register> = allocator.allocateRegisters(
            Liveness.LivenessGraphs(
                Liveness.inducedSubgraph(livenessGraphs.interferenceGraph, spilledRegisters),
                Liveness.inducedSubgraph(livenessGraphs.copyGraph, spilledRegisters),
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
            .map { it.spilledUsedRegisters + it.spilledDefinedRegisters }
            .forEach { spilledRegisters -> spilledRegisters.withIndex().forEach { newAllocation[it.value] = reservedRegisters[it.index] } }
        return newAllocation
    }

    private fun transformProgram(
        linearProgram: List<Asmable>,
        instructionsToSpilled: Map<Int, SpilledInstruction>,
        allocatedRegisters: Map<Register, Register>,
        spillsColoring: Map<Register, ULong>,
    ): List<Asmable> {
        val newLinearProgram = mutableListOf<Asmable>()

        fun handleSpilledInstruction(spilledInstruction: SpilledInstruction) {
            spilledInstruction.spilledUsedRegisters.forEach {
                newLinearProgram.add(
                    Instruction.InPlaceInstruction.MoveRM(
                        allocatedRegisters[it]!!,
                        Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(spillsColoring[it]!! * memoryUnitSize)),
                    )
                )
            }

            newLinearProgram.add(spilledInstruction.instruction)

            spilledInstruction.spilledDefinedRegisters.forEach {
                newLinearProgram.add(
                    Instruction.InPlaceInstruction.MoveMR(
                        Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(spillsColoring[it]!! * memoryUnitSize)),
                        allocatedRegisters[it]!!,
                    )
                )
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
