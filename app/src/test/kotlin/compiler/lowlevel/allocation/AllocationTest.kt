package compiler.lowlevel.allocation

import compiler.intermediate.Register
import compiler.intermediate.generators.MEMORY_UNIT_SIZE
import compiler.lowlevel.Addressing
import compiler.lowlevel.Instruction
import compiler.lowlevel.Label
import compiler.lowlevel.dataflow.Liveness
import kotlin.test.Test
import kotlin.test.assertEquals

class AllocationTest {

    private val reg1 = Register()
    private val reg2 = Register()
    private val reg3 = Register()
    private val reg4 = Register()

    private val phReg1 = Register()
    private val phReg2 = Register()
    private val phReg3 = Register()
    private val phReg4 = Register()

    private val phRegs = listOf(phReg1, phReg2, phReg3, phReg4)

    private val variableBlockSize: ULong = 24U
    private fun getSpillOffset(index: UInt) = -(variableBlockSize + index * MEMORY_UNIT_SIZE).toInt()

    @Test
    fun `test program which uses no registers`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(),
            Instruction.TerminalInstruction.Dummy(),
            Instruction.UnconditionalJumpInstruction.Dummy("targetLabel"),
            Instruction.ConditionalJumpInstruction.Dummy("targetLabel"),
            Label("targetLabel"),
        )
        val livenessGraphs = Liveness.LivenessGraphs(mapOf(), mapOf())
        val orderedPhysicalRegisters = listOf<Register>()
        val allocator = object : PartialAllocation {
            override fun allocateRegisters(
                livenessGraphs: Liveness.LivenessGraphs,
                selfAllocatedRegisters: List<Register>,
                availableRegisters: List<Register>
            ): PartialAllocation.AllocationResult = PartialAllocation.AllocationResult(mapOf(), listOf())
        }

        val result = Allocation(allocator).allocateRegistersWithSpillsHandling(
            linearProgram,
            livenessGraphs,
            orderedPhysicalRegisters,
            orderedPhysicalRegisters,
            orderedPhysicalRegisters,
            variableBlockSize
        )

        assertEquals(mapOf(), result.allocatedRegisters)
        assertEquals(linearProgram, result.code)
        assertEquals(0u, result.spilledOffset)
    }

    @Test
    fun `test program with an instruction with spilled registers`() {
        val linearProgram = listOf(
            Label("targetLabel"),
            Instruction.InPlaceInstruction.Dummy(
                regsUsed = listOf(reg1, reg2),
                regsDefined = listOf(reg1, reg3),
            ),
            Instruction.InPlaceInstruction.Dummy(
                regsDefined = listOf(phReg4),
            ),
            Instruction.TerminalInstruction.Dummy(),
        )
        val livenessGraphs = Liveness.LivenessGraphs(mapOf(), mapOf())
        val orderedPhysicalRegisters = listOf(phReg1, phReg2, phReg3)
        val allocator = object : PartialAllocation {
            // First time there are too few reserved registers (0)
            // Second time there are too few reserved registers (1)
            // Third time there are too few reserved registers (2)
            // Fourth time is ok
            // Fifth time is a call to color the spills
            var usages = 0
            override fun allocateRegisters(
                livenessGraphs: Liveness.LivenessGraphs,
                selfAllocatedRegisters: List<Register>,
                availableRegisters: List<Register>
            ): PartialAllocation.AllocationResult {
                return if (usages >= 4) {
                    PartialAllocation.AllocationResult(
                        mapOf(reg1 to availableRegisters[0], reg2 to availableRegisters[1], reg3 to availableRegisters[2]),
                        listOf(),
                    )
                } else {
                    usages ++
                    PartialAllocation.AllocationResult(
                        mapOf(phReg4 to phReg4),
                        listOf(reg1, reg2, reg3),
                    )
                }
            }
        }

        val result = Allocation(allocator).allocateRegistersWithSpillsHandling(
            linearProgram,
            livenessGraphs,
            orderedPhysicalRegisters,
            orderedPhysicalRegisters,
            orderedPhysicalRegisters,
            variableBlockSize
        )

        assertEquals(
            mapOf(
                phReg1 to phReg1,
                phReg2 to phReg2,
                phReg3 to phReg3,
                phReg4 to phReg4,
                reg1 to phReg1,
                reg2 to phReg2,
                reg3 to phReg3,
            ),
            result.allocatedRegisters
        )
        assertEquals(
            listOf(
                linearProgram[0],
                Instruction.InPlaceInstruction.MoveRM(
                    phReg1,
                    Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(getSpillOffset(1u))),
                ),
                Instruction.InPlaceInstruction.MoveRM(
                    phReg2,
                    Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(getSpillOffset(2u))),
                ),
                linearProgram[1],
                Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(getSpillOffset(1u))),
                    phReg1,
                ),
                Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(getSpillOffset(3u))),
                    phReg3,
                ),
                linearProgram[2],
                linearProgram[3],
            ),
            result.code
        )
        assertEquals(3u * MEMORY_UNIT_SIZE, result.spilledOffset)
    }

    @Test
    fun `test program where physical registers are reused a lot`() {
        val linearProgram = listOf(
            Label("targetLabel"),
            Instruction.InPlaceInstruction.Dummy(
                regsUsed = listOf(reg1),
            ),
            Instruction.InPlaceInstruction.Dummy(
                regsUsed = listOf(reg2),
            ),
            Instruction.InPlaceInstruction.Dummy(
                regsUsed = listOf(reg3),
            ),
            Instruction.TerminalInstruction.Dummy(),
        )
        val livenessGraphs = Liveness.LivenessGraphs(mapOf(), mapOf())
        val orderedPhysicalRegisters = listOf(phReg1)
        val allocator = object : PartialAllocation {
            // First time there are too few reserved registers (0)
            // Second time is ok
            // Third time is a call to color the spills
            var usages = 0
            override fun allocateRegisters(
                livenessGraphs: Liveness.LivenessGraphs,
                selfAllocatedRegisters: List<Register>,
                availableRegisters: List<Register>
            ): PartialAllocation.AllocationResult {
                return if (usages >= 2) {
                    PartialAllocation.AllocationResult(
                        mapOf(reg1 to availableRegisters[0], reg2 to availableRegisters[1], reg3 to availableRegisters[2]),
                        listOf(),
                    )
                } else {
                    usages ++
                    PartialAllocation.AllocationResult(
                        mapOf(phReg4 to phReg4),
                        listOf(reg1, reg2, reg3),
                    )
                }
            }
        }

        val result = Allocation(allocator).allocateRegistersWithSpillsHandling(
            linearProgram,
            livenessGraphs,
            phRegs,
            orderedPhysicalRegisters,
            orderedPhysicalRegisters,
            variableBlockSize
        )

        assertEquals(
            mapOf(
                phReg1 to phReg1,
                phReg4 to phReg4,
                reg1 to phReg1,
                reg2 to phReg1,
                reg3 to phReg1,
            ),
            result.allocatedRegisters
        )
        assertEquals(
            listOf(
                linearProgram[0],
                Instruction.InPlaceInstruction.MoveRM(
                    phReg1,
                    Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(getSpillOffset(1u))),
                ),
                linearProgram[1],
                Instruction.InPlaceInstruction.MoveRM(
                    phReg1,
                    Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(getSpillOffset(2u))),
                ),
                linearProgram[2],
                Instruction.InPlaceInstruction.MoveRM(
                    phReg1,
                    Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(getSpillOffset(3u))),
                ),
                linearProgram[3],
                linearProgram[4],
            ),
            result.code
        )
        assertEquals(3u * MEMORY_UNIT_SIZE, result.spilledOffset)
    }

    @Test
    fun `test program where coloring optimizes the memory usage`() {
        val linearProgram = listOf(
            Label("targetLabel"),
            Instruction.InPlaceInstruction.Dummy(
                regsDefined = listOf(reg1, reg2),
            ),
            Instruction.InPlaceInstruction.Dummy(
                regsDefined = listOf(reg3, reg4),
            ),
            Instruction.TerminalInstruction.Dummy(),
        )
        val livenessGraphs = Liveness.LivenessGraphs(mapOf(), mapOf())
        val allocatablePhysicalRegisters = listOf(phReg1, phReg2)
        val allocator = object : PartialAllocation {
            // First time there are too few reserved registers (0)
            // Second time there are too few reserved registers (1)
            // Third time is ok
            // Fourth time is a call to color the spills
            var usages = 0
            override fun allocateRegisters(
                livenessGraphs: Liveness.LivenessGraphs,
                selfAllocatedRegisters: List<Register>,
                availableRegisters: List<Register>
            ): PartialAllocation.AllocationResult {
                return if (usages >= 3) {
                    PartialAllocation.AllocationResult(
                        mapOf(reg1 to availableRegisters[0], reg2 to availableRegisters[1], reg3 to availableRegisters[0], reg4 to availableRegisters[1]),
                        listOf(),
                    )
                } else {
                    usages ++
                    PartialAllocation.AllocationResult(
                        mapOf(phReg4 to phReg4),
                        listOf(reg1, reg2, reg3, reg4),
                    )
                }
            }
        }

        val result = Allocation(allocator).allocateRegistersWithSpillsHandling(
            linearProgram,
            livenessGraphs,
            phRegs,
            allocatablePhysicalRegisters,
            allocatablePhysicalRegisters,
            variableBlockSize
        )

        assertEquals(
            mapOf(
                phReg1 to phReg1,
                phReg2 to phReg2,
                phReg4 to phReg4,
                reg1 to phReg1,
                reg2 to phReg2,
                reg3 to phReg1,
                reg4 to phReg2,
            ),
            result.allocatedRegisters
        )
        assertEquals(
            listOf(
                linearProgram[0],
                linearProgram[1],
                Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(getSpillOffset(1u))),
                    phReg1,
                ),
                Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(getSpillOffset(2u))),
                    phReg2,
                ),
                linearProgram[2],
                Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(getSpillOffset(1u))),
                    phReg1,
                ),
                Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RBP, Addressing.MemoryAddress.Const(getSpillOffset(2u))),
                    phReg2,
                ),
                linearProgram[3],
            ),
            result.code
        )
        assertEquals(2u * MEMORY_UNIT_SIZE, result.spilledOffset)
    }
}
