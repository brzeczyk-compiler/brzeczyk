package compiler.lowlevel.allocation

import compiler.intermediate.Register
import compiler.intermediate.generators.memoryUnitSize
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

    @Test
    fun `test program which uses no registers`() {
        val linearProgram = listOf(
            Instruction.InPlaceInstruction.Dummy(),
            Instruction.RetInstruction.Dummy(),
            Instruction.UnconditionalJumpInstruction.Dummy("targetLabel"),
            Instruction.ConditionalJumpInstruction.Dummy("targetLabel"),
            Label("targetLabel"),
        )
        val livenessGraphs = Liveness.LivenessGraphs(mapOf(), mapOf())
        val orderedPhysicalRegisters = listOf<Register>()
        val allocator = object : PartialAllocation {
            override fun allocateRegisters(
                livenessGraphs: Liveness.LivenessGraphs,
                accessibleRegisters: List<Register>
            ): PartialAllocation.AllocationResult = PartialAllocation.AllocationResult(mapOf(), listOf())
        }

        val result = Allocation.allocateRegistersWithSpillsHandling(
            linearProgram,
            livenessGraphs,
            orderedPhysicalRegisters,
            allocator,
        )

        assertEquals(mapOf(), result.allocatedRegisters)
        assertEquals(linearProgram, result.linearProgram)
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
            Instruction.RetInstruction.Dummy(),
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
                accessibleRegisters: List<Register>
            ): PartialAllocation.AllocationResult {
                return if (usages >= 4) {
                    PartialAllocation.AllocationResult(
                        mapOf(reg1 to accessibleRegisters[0], reg2 to accessibleRegisters[1], reg3 to accessibleRegisters[2]),
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

        val result = Allocation.allocateRegistersWithSpillsHandling(
            linearProgram,
            livenessGraphs,
            orderedPhysicalRegisters,
            allocator,
        )

        assertEquals(
            mapOf(
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
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(1u * memoryUnitSize)),
                ),
                Instruction.InPlaceInstruction.MoveRM(
                    phReg2,
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(2u * memoryUnitSize)),
                ),
                linearProgram[1],
                Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(1u * memoryUnitSize)),
                    phReg1,
                ),
                Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(3u * memoryUnitSize)),
                    phReg3,
                ),
                linearProgram[2],
                linearProgram[3],
            ),
            result.linearProgram
        )
        assertEquals(3u * memoryUnitSize, result.spilledOffset)
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
            Instruction.RetInstruction.Dummy(),
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
                accessibleRegisters: List<Register>
            ): PartialAllocation.AllocationResult {
                return if (usages >= 2) {
                    PartialAllocation.AllocationResult(
                        mapOf(reg1 to accessibleRegisters[0], reg2 to accessibleRegisters[1], reg3 to accessibleRegisters[2]),
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

        val result = Allocation.allocateRegistersWithSpillsHandling(
            linearProgram,
            livenessGraphs,
            orderedPhysicalRegisters,
            allocator,
        )

        assertEquals(
            mapOf(
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
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(1u * memoryUnitSize)),
                ),
                linearProgram[1],
                Instruction.InPlaceInstruction.MoveRM(
                    phReg1,
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(2u * memoryUnitSize)),
                ),
                linearProgram[2],
                Instruction.InPlaceInstruction.MoveRM(
                    phReg1,
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(3u * memoryUnitSize)),
                ),
                linearProgram[3],
                linearProgram[4],
            ),
            result.linearProgram
        )
        assertEquals(3u * memoryUnitSize, result.spilledOffset)
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
            Instruction.RetInstruction.Dummy(),
        )
        val livenessGraphs = Liveness.LivenessGraphs(mapOf(), mapOf())
        val orderedPhysicalRegisters = listOf(phReg1, phReg2)
        val allocator = object : PartialAllocation {
            // First time there are too few reserved registers (0)
            // Second time there are too few reserved registers (1)
            // Third time is ok
            // Fourth time is a call to color the spills
            var usages = 0
            override fun allocateRegisters(
                livenessGraphs: Liveness.LivenessGraphs,
                accessibleRegisters: List<Register>
            ): PartialAllocation.AllocationResult {
                return if (usages >= 3) {
                    PartialAllocation.AllocationResult(
                        mapOf(reg1 to accessibleRegisters[0], reg2 to accessibleRegisters[1], reg3 to accessibleRegisters[0], reg4 to accessibleRegisters[1]),
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

        val result = Allocation.allocateRegistersWithSpillsHandling(
            linearProgram,
            livenessGraphs,
            orderedPhysicalRegisters,
            allocator,
        )

        assertEquals(
            mapOf(
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
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(1u * memoryUnitSize)),
                    phReg1,
                ),
                Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(2u * memoryUnitSize)),
                    phReg2,
                ),
                linearProgram[2],
                Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(1u * memoryUnitSize)),
                    phReg1,
                ),
                Instruction.InPlaceInstruction.MoveMR(
                    Addressing.Base(Register.RSP, Addressing.MemoryAddress.Const(2u * memoryUnitSize)),
                    phReg2,
                ),
                linearProgram[3],
            ),
            result.linearProgram
        )
        assertEquals(2u * memoryUnitSize, result.spilledOffset)
    }
}
