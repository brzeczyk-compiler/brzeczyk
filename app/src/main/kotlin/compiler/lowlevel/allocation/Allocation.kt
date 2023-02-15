package compiler.lowlevel.allocation

import compiler.intermediate.Register
import compiler.intermediate.generators.MEMORY_UNIT_SIZE
import compiler.lowlevel.Addressing
import compiler.lowlevel.Asmable
import compiler.lowlevel.Instruction
import compiler.lowlevel.Label
import compiler.lowlevel.dataflow.Liveness

class Allocation(private val allocator: PartialAllocation) {
    companion object {
        val HARDWARE_REGISTERS = listOf(
            Register.R15,
            Register.R14,
            Register.R13,
            Register.R12,
            Register.R11,
            Register.R10,
            Register.RBP,
            Register.RSP,
            Register.R9,
            Register.R8,
            Register.RDI,
            Register.RSI,
            Register.RDX,
            Register.RCX,
            Register.RBX,
            Register.RAX,
        )

        val AVAILABLE_REGISTERS = listOf(
            Register.R15,
            Register.R14,
            Register.R13,
            Register.R12,
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
        )

        val POTENTIAL_SPILL_HANDLING_REGISTERS = listOf(
            Register.R11,
            Register.R10,
            Register.R15,
            Register.R14,
            Register.R13,
            Register.R12,
            Register.RBX,
        )
    }

    data class Result(
        val allocatedRegisters: Map<Register, Register>,
        val code: List<Asmable>,
        val spilledOffset: ULong,
    )

    fun allocateRegistersWithSpillsHandling(
        linearProgram: List<Asmable>,
        livenessGraphs: Liveness.LivenessGraphs,
        hardwareRegisters: List<Register>,
        availableRegisters: List<Register>,
        potentialSpillHandlingRegisters: List<Register>,
        spilledRegistersRegionOffset: ULong
    ): Result {
        var reservedRegistersNumber = 0
        while (true) {
            val spillHandlingRegisters = potentialSpillHandlingRegisters.take(reservedRegistersNumber)
            val actuallyAvailableRegisters = availableRegisters - spillHandlingRegisters.toSet()
            val allocationResult = allocator.allocateRegisters(livenessGraphs, hardwareRegisters, actuallyAvailableRegisters)
            val spilledRegisters = allocationResult.spilledRegisters.toSet()

            val instructionsToSpilled: Map<Int, SpilledInstruction> = linearProgram
                .withIndex()
                .filter { it.value is Instruction }
                .associate { it.index to toSpilledInstruction(it.value as Instruction, spilledRegisters) }
            val maxInstructionSpill = instructionsToSpilled.values.maxOfOrNull { it.numberOfSpilledRegisters } ?: 0

            if (maxInstructionSpill <= reservedRegistersNumber) {

                val spilledRegistersColoring: Map<Register, ULong> = colorSpilledRegisters(
                    spilledRegisters,
                    livenessGraphs
                )

                val registerAllocation: Map<Register, Register> = allocateSpilledRegisters(
                    spillHandlingRegisters,
                    allocationResult.allocatedRegisters,
                    instructionsToSpilled.values.toList(),
                )

                val newLinearProgram: List<Asmable> = transformProgram(
                    linearProgram,
                    instructionsToSpilled,
                    registerAllocation,
                    spilledRegistersColoring,
                    spilledRegistersRegionOffset
                ).filterNot {
                    it is Instruction.InPlaceInstruction.MoveRR &&
                        registerAllocation[it.regDest] == registerAllocation[it.regSrc]
                }

                return Result(
                    registerAllocation,
                    newLinearProgram,
                    (spilledRegistersColoring.values.maxOrNull() ?: 0u) * MEMORY_UNIT_SIZE,
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
        livenessGraphs: Liveness.LivenessGraphs
    ): Map<Register, ULong> {
        val spillsPossibleColors = spilledRegisters.map { Register() }
        val spillsColoring: Map<Register, Register> = allocator.allocateRegisters(
            Liveness.LivenessGraphs(
                Liveness.inducedSubgraph(livenessGraphs.interferenceGraph, spilledRegisters),
                Liveness.inducedSubgraph(livenessGraphs.copyGraph, spilledRegisters),
            ),
            spillsPossibleColors,
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
        newAllocation.putAll(reservedRegisters.associateWith { it })
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
        spillMemoryRegionOffset: ULong
    ): List<Asmable> {
        val newLinearProgram = mutableListOf<Asmable>()

        fun spilledRegisterAddress(register: Register): Addressing =
            Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(-(spillMemoryRegionOffset + spillsColoring[register]!! * MEMORY_UNIT_SIZE).toInt()))

        fun handleSpilledInstruction(spilledInstruction: SpilledInstruction) {
            spilledInstruction.spilledUsedRegisters.forEach {
                newLinearProgram.add(
                    Instruction.InPlaceInstruction.MoveRM(allocatedRegisters[it]!!, spilledRegisterAddress(it))
                )
            }

            newLinearProgram.add(spilledInstruction.instruction)

            spilledInstruction.spilledDefinedRegisters.forEach {
                newLinearProgram.add(
                    Instruction.InPlaceInstruction.MoveMR(spilledRegisterAddress(it), allocatedRegisters[it]!!)
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
