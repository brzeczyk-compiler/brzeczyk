package compiler.lowlevel

import compiler.intermediate.Constant
import compiler.intermediate.FixedConstant
import compiler.intermediate.Register
import java.io.PrintWriter

sealed class Instruction : Asmable {
    open val regsUsed: Collection<Register> = emptyList()
    open val regsDefined: Collection<Register> = emptyList()

    // Returns the set of registers used in memory address
    fun regsUsedInAddress(address: Addressing): Collection<Register> {
        return when (address) {
            is Addressing.Base -> setOf(address.base)
            is Addressing.IndexAndDisplacement -> setOf(address.index)
            is Addressing.BaseAndIndex -> setOf(address.base, address.index)
            else -> emptySet()
        }
    }

    abstract fun toAsm(registers: Map<Register, Register>): String

    override fun writeAsm(output: PrintWriter, registers: Map<Register, Register>) = output.write(toAsm(registers))

    class DummyInstructionIsNotAsmable : Throwable()

    sealed class ConditionalJumpInstruction : Instruction() {
        abstract val targetLabel: String

        override fun toAsm(registers: Map<Register, Register>) = when (this) {
            is JmpEq -> "je"
            is JmpNEq -> "jne"
            is JmpLt -> "jl"
            is JmpLtEq -> "jle"
            is JmpGt -> "jg"
            is JmpGtEq -> "jge"
            is JmpZ -> "jz"
            is JmpNZ -> "jnz"
            is Dummy -> throw DummyInstructionIsNotAsmable()
        } + " $targetLabel"

        data class JmpEq(override val targetLabel: String) : ConditionalJumpInstruction() //   JE   targetLabel
        data class JmpNEq(override val targetLabel: String) : ConditionalJumpInstruction() //  JNE  targetLabel
        data class JmpLt(override val targetLabel: String) : ConditionalJumpInstruction() //   JL   targetLabel
        data class JmpLtEq(override val targetLabel: String) : ConditionalJumpInstruction() // JLE  targetLabel
        data class JmpGt(override val targetLabel: String) : ConditionalJumpInstruction() //   JG   targetLabel
        data class JmpGtEq(override val targetLabel: String) : ConditionalJumpInstruction() // JGE  targetLabel
        data class JmpZ(override val targetLabel: String) : ConditionalJumpInstruction() //    JZ   targetLabel
        data class JmpNZ(override val targetLabel: String) : ConditionalJumpInstruction() //   JNZ  targetLabel
        data class Dummy(override val targetLabel: String) : ConditionalJumpInstruction()
    }

    sealed class UnconditionalJumpInstruction : Instruction() {
        abstract val targetLabel: String

        override fun toAsm(registers: Map<Register, Register>) = when (this) {
            is JmpL -> "jmp"
            is Dummy -> throw DummyInstructionIsNotAsmable()
        } + " $targetLabel"

        data class JmpL(override val targetLabel: String) : UnconditionalJumpInstruction() //   JMP  targetLabel
        data class Dummy(override val targetLabel: String) : UnconditionalJumpInstruction()
    }

    sealed class TerminalInstruction : Instruction() {

        override fun toAsm(registers: Map<Register, Register>) = when (this) {
            is Ret -> "ret"
            is JmpR -> "jmp ${registers[targetRegister]!!.toAsm()}"
            is Dummy -> throw DummyInstructionIsNotAsmable()
        }

        data class Ret(override val regsUsed: Collection<Register>) : TerminalInstruction() // RET
        data class JmpR(val targetRegister: Register) : TerminalInstruction() { // JMP targetRegister
            override val regsUsed: Collection<Register> = listOf(targetRegister)
        }
        data class Dummy(val dummy: Unit = Unit) : TerminalInstruction()
    }

    sealed class InPlaceInstruction : Instruction() {

        // Move instructions
        data class MoveRR(val regDest: Register, val regSrc: Register) : InPlaceInstruction() { //   MOV  reg_dest, reg_src
            override val regsDefined: Collection<Register> = setOf(regDest)
            override val regsUsed: Collection<Register> = setOf(regSrc)

            override fun toAsm(registers: Map<Register, Register>) = "mov ${registers[regDest]!!.toAsm()}, ${registers[regSrc]!!.toAsm()}"
        }
        data class MoveRM(val regDest: Register, val memSrc: Addressing) : InPlaceInstruction() { // MOV  reg_dest, mem_src
            override val regsDefined: Collection<Register> = setOf(regDest)
            override val regsUsed: Collection<Register> = regsUsedInAddress(memSrc)

            override fun toAsm(registers: Map<Register, Register>) = "mov ${registers[regDest]!!.toAsm()}, ${memSrc.toAsm(registers)}"
        }
        data class MoveMR(val memDest: Addressing, val regSrc: Register) : InPlaceInstruction() { // MOV  mem_dest, reg_src
            override val regsUsed: Collection<Register> = setOf(regSrc) union regsUsedInAddress(memDest)

            override fun toAsm(registers: Map<Register, Register>) = "mov ${memDest.toAsm(registers)}, ${registers[regSrc]!!.toAsm()}"
        }
        data class MoveRI(val regDest: Register, val constant: Constant) : InPlaceInstruction() { //      MOV  reg_dest, constant
            constructor(regDest: Register, constant: Long) : this(regDest, FixedConstant(constant))
            override val regsDefined: Collection<Register> = setOf(regDest)

            override fun toAsm(registers: Map<Register, Register>) = "mov ${registers[regDest]!!.toAsm()}, ${constant.value}"
        }

        // The LEA instruction
        data class Lea(val reg: Register, val address: Addressing) : InPlaceInstruction() { // LEA  reg,  mem
            override val regsDefined: Collection<Register> = setOf(reg)
            override val regsUsed: Collection<Register> = regsUsedInAddress(address)

            override fun toAsm(registers: Map<Register, Register>) = "lea ${registers[reg]!!.toAsm()}, ${address.toAsm(registers)}"
        }

        // CALL instructions
        data class CallR(
            val reg: Register,
            override val regsUsed: Collection<Register>,
            override val regsDefined: Collection<Register>,
        ) : InPlaceInstruction() { //               CALL reg
            override fun toAsm(registers: Map<Register, Register>) = "call ${registers[reg]!!.toAsm()}"
        }
        data class CallL(
            val targetLabel: String,
            override val regsUsed: Collection<Register>,
            override val regsDefined: Collection<Register>,
        ) : InPlaceInstruction() { //           CALL targetLabel
            override fun toAsm(registers: Map<Register, Register>) = "call $targetLabel"
        }

        // Stack instructions
        data class PushR(val reg: Register) : InPlaceInstruction() { //               PUSH reg
            override val regsUsed: Collection<Register> = setOf(reg)

            override fun toAsm(registers: Map<Register, Register>) = "push ${registers[reg]!!.toAsm()}"
        }
        data class PushM(val address: Addressing) : InPlaceInstruction() { //         PUSH mem
            override val regsUsed: Collection<Register> = regsUsedInAddress(address)

            override fun toAsm(registers: Map<Register, Register>) = "push ${address.toAsm(registers)}"
        }
        data class PushI(val constant: Constant) : InPlaceInstruction() { //                PUSH imm
            constructor(constant: Long) : this(FixedConstant(constant))

            override fun toAsm(registers: Map<Register, Register>) = "push ${constant.value}"
        }
        data class PopR(val reg: Register) : InPlaceInstruction() { //                POP  reg
            override val regsDefined: Collection<Register> = setOf(reg)

            override fun toAsm(registers: Map<Register, Register>) = "pop ${registers[reg]!!.toAsm()}"
        }
        data class PopM(val address: Addressing) : InPlaceInstruction() { //          POP  mem
            override val regsUsed: Collection<Register> = regsUsedInAddress(address)

            override fun toAsm(registers: Map<Register, Register>) = "pop ${address.toAsm(registers)}"
        }

        // Helper parent class for instructions that take a register, use it and store a value in it
        sealed class OneRegIns : InPlaceInstruction() {
            abstract val reg: Register
            override val regsDefined: Collection<Register> get() = setOf(reg)
            override val regsUsed: Collection<Register> get() = setOf(reg)

            override fun toAsm(registers: Map<Register, Register>) = when (this) {
                is NegR -> "neg"
                is NotR -> "not"
            } + " ${registers[reg]!!.toAsm()}"
        }

        // Helper parent class for instructions that take two registers, use them and store a value in the first one
        sealed class TwoRegIns : InPlaceInstruction() {
            abstract val reg0: Register
            abstract val reg1: Register
            override val regsDefined: Collection<Register> get() = setOf(reg0)
            override val regsUsed: Collection<Register> get() = setOf(reg0, reg1)

            override fun toAsm(registers: Map<Register, Register>) = when (this) {
                is AddRR -> "add"
                is AndRR -> "and"
                is MulRR -> "imul"
                is OrRR -> "or"
                is SubRR -> "sub"
                is XorRR -> "xor"
            } + " ${registers[reg0]!!.toAsm()}, ${registers[reg1]!!.toAsm()}"
        }

        // Arithmetic instructions
        data class NegR(override val reg: Register) : OneRegIns() //                                NEG  reg          reg  <- -reg
        data class AddRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() // ADD  reg0, reg1   reg0 <- reg0 + reg1
        data class SubRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() // SUB  reg0, reg1   reg0 <- reg0 - reg1
        data class MulRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() // IMUL reg0, reg1   reg0 <- reg0 * reg1
        data class DivR(val reg: Register) : InPlaceInstruction() { //                              IDIV reg          RAX  <- RDX:RAX / reg
            override val regsDefined: Collection<Register> = setOf(Register.RAX, Register.RDX) //                     RDX  <- RDX:RAX % reg
            override val regsUsed: Collection<Register> = setOf(Register.RAX, Register.RDX, reg)

            override fun toAsm(registers: Map<Register, Register>) = "idiv ${registers[reg]!!.toAsm()}"
        }
        // CQO - copies the sign bit in RAX to all bits in RDX
        data class Cqo(val dummy: Unit = Unit) : InPlaceInstruction() {
            override val regsDefined: Collection<Register> = setOf(Register.RDX)
            override val regsUsed: Collection<Register> = setOf(Register.RAX)

            override fun toAsm(registers: Map<Register, Register>) = "cqo"
        }
        // Bitwise instructions
        data class NotR(override val reg: Register) : OneRegIns() //                                NOT  reg          reg  <- ~reg
        data class AndRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() // AND  reg0, reg1   reg0 <- reg0 & reg1
        data class OrRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() //  OR   reg0, reg1   reg0 <- reg0 & reg1
        data class XorRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() // XOR  reg0, reg1   reg0 <- reg0 ^ reg1
        data class ShiftLeftR(val reg: Register) : InPlaceInstruction() { //                        SAL  reg,  CL     reg  <- reg << CL
            override val regsDefined: Collection<Register> = setOf(reg)
            override val regsUsed: Collection<Register> = setOf(reg, Register.RCX)

            override fun toAsm(registers: Map<Register, Register>) = "sal ${registers[reg]!!.toAsm()}, cl"
        }
        data class ShiftRightR(val reg: Register) : InPlaceInstruction() { //                       SAR  reg,  CL     reg  <- reg >> CL
            override val regsDefined: Collection<Register> = setOf(reg)
            override val regsUsed: Collection<Register> = setOf(reg, Register.RCX)

            override fun toAsm(registers: Map<Register, Register>) = "sar ${registers[reg]!!.toAsm()}, cl"
        }

        // Comparison instructions
        data class CmpRR(val reg0: Register, val reg1: Register) : InPlaceInstruction() { //  CMP   reg0, reg1
            override val regsUsed: Collection<Register> = setOf(reg0, reg1)

            override fun toAsm(registers: Map<Register, Register>) = "cmp ${registers[reg0]!!.toAsm()}, ${registers[reg1]!!.toAsm()}"
        }
        data class TestRR(val reg0: Register, val reg1: Register) : InPlaceInstruction() { // TEST  reg0, reg1
            override val regsUsed: Collection<Register> = setOf(reg0, reg1)

            override fun toAsm(registers: Map<Register, Register>) = "test ${registers[reg0]!!.toAsm()}, ${registers[reg1]!!.toAsm()}"
        }

        // SETcc instructions
        // They take 8-bit registers as operands, so reg8 in SETcc reg8 refers to low 8-bit register corresponding to reg
        sealed class SetInstruction : InPlaceInstruction() {
            abstract val reg: Register
            override val regsDefined: Collection<Register> get() = setOf(reg)
            override val regsUsed: Collection<Register> get() = setOf(reg)

            override fun toAsm(registers: Map<Register, Register>) = when (this) {
                is SetEqR -> "sete"
                is SetNeqR -> "setne"
                is SetLtR -> "setl"
                is SetLtEqR -> "setle"
                is SetGtR -> "setg"
                is SetGtEqR -> "setge"
            } + " ${registers[reg]!!.to8bitLower()}"
        }
        data class SetEqR(override val reg: Register) : SetInstruction() //   SETE  reg8
        data class SetNeqR(override val reg: Register) : SetInstruction() //  SETNE reg8
        data class SetLtR(override val reg: Register) : SetInstruction() //   SETL  reg8
        data class SetLtEqR(override val reg: Register) : SetInstruction() // SETLE reg8
        data class SetGtR(override val reg: Register) : SetInstruction() //   SETG  reg8
        data class SetGtEqR(override val reg: Register) : SetInstruction() // SETGE reg8

        data class Dummy(override val regsUsed: List<Register> = listOf(), override val regsDefined: List<Register> = listOf()) : InPlaceInstruction() {
            override fun toAsm(registers: Map<Register, Register>) = throw DummyInstructionIsNotAsmable()
        }
    }
}
