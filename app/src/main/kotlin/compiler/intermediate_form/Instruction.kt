package compiler.intermediate_form

sealed class Instruction : Asmable {
    open val regsUsed: Collection<Register> = listOf()
    open val regsDefined: Collection<Register> = listOf()

    sealed class ConditionalJumpInstruction : Instruction() {
        abstract val targetLabel: String

        class Dummy(override val targetLabel: String) : ConditionalJumpInstruction()
    }

    sealed class UnconditionalJumpInstruction : Instruction() {
        abstract val targetLabel: String

        data class Jmp(override val targetLabel: String) : UnconditionalJumpInstruction() // JMP label

        data class CallR(val reg: Register) : UnconditionalJumpInstruction() { //                      CALL reg
            override val targetLabel: String = TODO()
        }

        data class CallM(val address: Addressing) : UnconditionalJumpInstruction() { //                CALL mem
            override val targetLabel: String = TODO()
        }

        class Dummy(override val targetLabel: String) : UnconditionalJumpInstruction()
    }

    sealed class RetInstruction : Instruction() {
        class Ret : RetInstruction() //                                                RET

        class Dummy : RetInstruction()
    }

    sealed class InPlaceInstruction : Instruction() {
        data class MoveRR(val reg_dest: Register, val reg_src: Register) : InPlaceInstruction() { //   MOV  reg_dest, reg_src
            override val regsDefined = listOf(reg_dest)
            override val regsUsed = listOf(reg_src)
        }
        data class MoveRM(val reg_dest: Register, val mem_src: Addressing) : InPlaceInstruction() // MOV  reg_dest, mem_src
        data class MoveMR(val mem_dest: Addressing, val reg_src: Register) : InPlaceInstruction() // MOV  mem_dest, reg_src
        data class MoveRI(val reg_dest: Register, val constant: Long) : InPlaceInstruction() //      MOV  reg_dest, constant

        data class Lea(val reg: Register, val address: Addressing) : InPlaceInstruction() //    LEA  reg,  mem

        data class PushR(val reg: Register) : InPlaceInstruction() //                      PUSH reg
        data class PushM(val address: Addressing) : InPlaceInstruction() //                PUSH mem
        data class PushI(val constant: Long) : InPlaceInstruction() //                     PUSH imm
        data class PopR(val reg: Register) : InPlaceInstruction() //                       POP  reg
        data class PopM(val address: Addressing) : InPlaceInstruction() //                 POP  mem

        data class NegR(val reg: Register) : InPlaceInstruction() //                       NEG  reg          reg  <- -reg
        data class AddRR(val reg0: Register, val reg1: Register) : InPlaceInstruction() // ADD  reg0, reg1   reg0 <- reg0 + reg1
        data class SubRR(val reg0: Register, val reg1: Register) : InPlaceInstruction() // SUB  reg0, reg1   reg0 <- reg0 - reg1
        data class MulRR(val reg0: Register, val reg1: Register) : InPlaceInstruction() // IMUL reg0, reg1   reg0 <- reg0 * reg1
        data class DivR(val reg: Register) : InPlaceInstruction() //                       IDIV reg          RAX  <- RDX:RAX / reg
        //                                                                                                   RDX  <- RDX:RAX % reg

        data class NotR(val reg0: Register) : InPlaceInstruction() //                      NOT  reg          reg  <- ~reg
        data class AndRR(val reg0: Register, val reg1: Register) : InPlaceInstruction() // AND  reg0, reg1   reg0 <- reg0 & reg1
        data class OrRR(val reg0: Register, val reg1: Register) : InPlaceInstruction() //  OR   reg0, reg1   reg0 <- reg0 & reg1
        data class XorRR(val reg0: Register, val reg1: Register) : InPlaceInstruction() // XOR  reg0, reg1   reg0 <- reg0 ^ reg1
        data class ShiftLeftR(val reg: Register) : InPlaceInstruction() //                 SAL  reg,  CL     reg  <- reg << CL
        data class ShiftRightR(val reg: Register) : InPlaceInstruction() //                SAR  reg,  CL     reg  <- reg >> CL

        data class CmpR(val reg0: Register, val reg1: Register) : InPlaceInstruction() //  CMP   reg0, reg1
        data class SetEqR(val reg: Register) : InPlaceInstruction() //                     SETE  reg
        data class SetNeqR(val reg: Register) : InPlaceInstruction() //                    SETNE reg
        data class SetLtR(val reg: Register) : InPlaceInstruction() //                     SETL  reg
        data class SetLtEqR(val reg: Register) : InPlaceInstruction() //                   SETLE reg
        data class SetGtR(val reg: Register) : InPlaceInstruction() //                     SETG  reg
        data class SetGtEqR(val reg: Register) : InPlaceInstruction() //                   SETGE reg

        class Dummy(override val regsUsed: List<Register> = listOf(), override val regsDefined: List<Register> = listOf()) : InPlaceInstruction()
    }
}
