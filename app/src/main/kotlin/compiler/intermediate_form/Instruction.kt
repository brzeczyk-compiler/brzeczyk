package compiler.intermediate_form

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

    sealed class ConditionalJumpInstruction : Instruction() {
        abstract val targetLabel: String
        data class JmpEq(override val targetLabel: String) : ConditionalJumpInstruction() //   JE   targetLabel
        data class JmpNEq(override val targetLabel: String) : ConditionalJumpInstruction() //  JNE  targetLabel
        data class JmpLt(override val targetLabel: String) : ConditionalJumpInstruction() //   JL   targetLabel
        data class JmpLtEq(override val targetLabel: String) : ConditionalJumpInstruction() // JLE  targetLabel
        data class JmpGt(override val targetLabel: String) : ConditionalJumpInstruction() //   JG   targetLabel
        data class JmpGtEq(override val targetLabel: String) : ConditionalJumpInstruction() // JGE  targetLabel
        data class JmpZ(override val targetLabel: String) : ConditionalJumpInstruction() //    JZ   targetLabel
        data class JmpNZ(override val targetLabel: String) : ConditionalJumpInstruction() //   JNZ  targetLabel
    }

    sealed class UnconditionalJumpInstruction : Instruction() {
        abstract val targetLabel: String
        data class Jmp(override val targetLabel: String) : UnconditionalJumpInstruction() //   JMP  targetLabel
    }

    sealed class RetInstruction : Instruction() {
        class Ret : RetInstruction() // RET
    }

    sealed class InPlaceInstruction : Instruction() {

        // Move instructions
        data class MoveRR(val reg_dest: Register, val reg_src: Register) : InPlaceInstruction() { //   MOV  reg_dest, reg_src
            override val regsDefined: Collection<Register> = setOf(reg_dest)
            override val regsUsed: Collection<Register> = setOf(reg_src)
        }
        data class MoveRM(val reg_dest: Register, val mem_src: Addressing) : InPlaceInstruction() { // MOV  reg_dest, mem_src
            override val regsDefined: Collection<Register> = setOf(reg_dest)
            override val regsUsed: Collection<Register> = regsUsedInAddress(mem_src)
        }
        data class MoveMR(val mem_dest: Addressing, val reg_src: Register) : InPlaceInstruction() { // MOV  mem_dest, reg_src
            override val regsUsed: Collection<Register> = setOf(reg_src) union regsUsedInAddress(mem_dest)
        }
        data class MoveRI(val reg_dest: Register, val constant: Long) : InPlaceInstruction() { //      MOV  reg_dest, constant
            override val regsDefined: Collection<Register> = setOf(reg_dest)
        }

        // The LEA instruction
        data class Lea(val reg: Register, val address: Addressing) : InPlaceInstruction() { // LEA  reg,  mem
            override val regsDefined: Collection<Register> = setOf(reg)
            override val regsUsed: Collection<Register> = regsUsedInAddress(address)
        }

        // CALL instructions
        data class CallR(val reg: Register) : InPlaceInstruction() { //               CALL reg
            override val regsUsed: Collection<Register> = setOf(reg)
        }
        data class CallL(val targetLabel: String) : InPlaceInstruction() //           CALL targetLabel

        // Stack instructions
        data class PushR(val reg: Register) : InPlaceInstruction() { //               PUSH reg
            override val regsUsed: Collection<Register> = setOf(reg)
        }
        data class PushM(val address: Addressing) : InPlaceInstruction() { //         PUSH mem
            override val regsUsed: Collection<Register> = regsUsedInAddress(address)
        }
        data class PushI(val constant: Long) : InPlaceInstruction() //                PUSH imm
        data class PopR(val reg: Register) : InPlaceInstruction() { //                POP  reg
            override val regsDefined: Collection<Register> = setOf(reg)
        }
        data class PopM(val address: Addressing) : InPlaceInstruction() { //          POP  mem
            override val regsUsed: Collection<Register> = regsUsedInAddress(address)
        }

        // Helper parent class for instructions that take a register, use it and store a value in it
        sealed class OneRegIns : InPlaceInstruction() {
            abstract val reg: Register
            override val regsDefined: Collection<Register> get() = setOf(reg)
            override val regsUsed: Collection<Register> get() = setOf(reg)
        }

        // Helper parent class for instructions that take two registers, use them and store a value in the first one
        sealed class TwoRegIns : InPlaceInstruction() {
            abstract val reg0: Register
            abstract val reg1: Register
            override val regsDefined: Collection<Register> get() = setOf(reg0)
            override val regsUsed: Collection<Register> get() = setOf(reg0, reg1)
        }

        // Arithmetic instructions
        data class NegR(override val reg: Register) : OneRegIns() //                                NEG  reg          reg  <- -reg
        data class AddRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() // ADD  reg0, reg1   reg0 <- reg0 + reg1
        data class SubRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() // SUB  reg0, reg1   reg0 <- reg0 - reg1
        data class MulRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() // IMUL reg0, reg1   reg0 <- reg0 * reg1
        data class DivR(val reg: Register) : InPlaceInstruction() { //                              IDIV reg          RAX  <- RDX:RAX / reg
            override val regsDefined: Collection<Register> = setOf(Register.RAX, Register.RDX) //                     RDX  <- RDX:RAX % reg
            override val regsUsed: Collection<Register> = setOf(Register.RAX, Register.RDX, reg)
        }
        // CQO - copies the sign bit in RAX to all bits in RDX
        class Cqo() : InPlaceInstruction() {
            override val regsDefined: Collection<Register> = setOf(Register.RDX)
            override val regsUsed: Collection<Register> = setOf(Register.RAX)
        }
        // Bitwise instructions
        data class NotR(override val reg: Register) : OneRegIns() //                                NOT  reg          reg  <- ~reg
        data class AndRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() // AND  reg0, reg1   reg0 <- reg0 & reg1
        data class OrRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() //  OR   reg0, reg1   reg0 <- reg0 & reg1
        data class XorRR(override val reg0: Register, override val reg1: Register) : TwoRegIns() // XOR  reg0, reg1   reg0 <- reg0 ^ reg1
        data class ShiftLeftR(val reg: Register) : InPlaceInstruction() { //                        SAL  reg,  CL     reg  <- reg << CL
            override val regsDefined: Collection<Register> = setOf(reg)
            override val regsUsed: Collection<Register> = setOf(reg, Register.RCX)
        }
        data class ShiftRightR(val reg: Register) : InPlaceInstruction() { //                       SAR  reg,  CL     reg  <- reg >> CL
            override val regsDefined: Collection<Register> = setOf(reg)
            override val regsUsed: Collection<Register> = setOf(reg, Register.RCX)
        }

        // Comparison instructions
        data class CmpRR(val reg0: Register, val reg1: Register) : InPlaceInstruction() { //  CMP   reg0, reg1
            override val regsUsed: Collection<Register> = setOf(reg0, reg1)
        }
        data class TestRR(val reg0: Register, val reg1: Register) : InPlaceInstruction() { // TEST  reg0, reg1
            override val regsUsed: Collection<Register> = setOf(reg0, reg1)
        }

        // SETcc instructions
        sealed class SetInstruction : InPlaceInstruction() {
            abstract val reg: Register
            override val regsDefined: Collection<Register> get() = setOf(reg)
            // I'm unsure if this is needed
            // The SETcc instructions set the lower 8 bits of a register
            // so the value after SETcc is dependent on the value of reg before instruction.
            // Specifically, if this wasn't included in when doing
            // 1: XOR    reg,   reg   ; reset to zero
            // 2: CMP    rega,  regb
            // 3: SETcc  reg          ; set lower 8 bits depending on comparison
            // reg wouldn't be alive between 1 and 3
            // Is this correct?
            override val regsUsed: Collection<Register> get() = setOf(reg)
        }
        data class SetEqR(override val reg: Register) : SetInstruction() //   SETE  reg
        data class SetNeqR(override val reg: Register) : SetInstruction() //  SETNE reg
        data class SetLtR(override val reg: Register) : SetInstruction() //   SETL  reg
        data class SetLtEqR(override val reg: Register) : SetInstruction() // SETLE reg
        data class SetGtR(override val reg: Register) : SetInstruction() //   SETG  reg
        data class SetGtEqR(override val reg: Register) : SetInstruction() // SETGE reg
    }
}
