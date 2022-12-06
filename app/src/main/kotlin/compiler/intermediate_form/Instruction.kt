package compiler.intermediate_form

sealed class Instruction : AsmAble {

    abstract class ConditionalJumpInstruction : Instruction() {
        abstract val targetLabel: String
    }

    abstract class UnconditionalJumpInstruction : Instruction() {
        abstract val targetLabel: String

        data class CallR(val reg: Register) : Instruction() //                      CALL reg
        data class CallM(val address: Addressing) : Instruction() //                CALL mem
    }

    abstract class RetInstruction : Instruction() {
        class Ret : RetInstruction() //                                                RET
    }

    abstract class InPlaceInstruction : Instruction() {
        data class MoveRR(val reg_dest: Register, val reg_src: Register) : Instruction() //   MOV  reg_dest, reg_src
        data class MoveRM(val reg_dest: Register, val mem_src: Addressing) : Instruction() // MOV  reg_dest, mem_src
        data class MoveMR(val mem_dest: Addressing, val reg_src: Register) : Instruction() // MOV  mem_dest, reg_src
        data class MoveRI(val reg_dest: Register, val constant: Long) : Instruction() //      MOV  reg_dest, constant

        data class Lea(val reg: Register, val address: Addressing) : Instruction() //    LEA  reg,  mem

        data class PushR(val reg: Register) : Instruction() //                      PUSH reg
        data class PushM(val address: Addressing) : Instruction() //                PUSH mem
        data class PushI(val constant: Long) : Instruction() //                     PUSH imm
        data class PopR(val reg: Register) : Instruction() //                       POP  reg
        data class PopM(val address: Addressing) : Instruction() //                 POP  mem

        data class NegR(val reg: Register) : Instruction() //                       NEG  reg          reg  <- -reg
        data class AddRR(val reg0: Register, val reg1: Register) : Instruction() // ADD  reg0, reg1   reg0 <- reg0 + reg1
        data class SubRR(val reg0: Register, val reg1: Register) : Instruction() // SUB  reg0, reg1   reg0 <- reg0 - reg1
        data class MulRR(val reg0: Register, val reg1: Register) : Instruction() // IMUL reg0, reg1   reg0 <- reg0 * reg1
        data class DivR(val reg: Register) : Instruction() //                       IDIV reg          RAX  <- RDX:RAX / reg
        //                                                                                            RDX  <- RDX:RAX % reg

        data class NotR(val reg0: Register) : Instruction() //                      NOT  reg          reg  <- ~reg
        data class AndRR(val reg0: Register, val reg1: Register) : Instruction() // AND  reg0, reg1   reg0 <- reg0 & reg1
        data class OrRR(val reg0: Register, val reg1: Register) : Instruction() //  OR   reg0, reg1   reg0 <- reg0 & reg1
        data class XorRR(val reg0: Register, val reg1: Register) : Instruction() // XOR  reg0, reg1   reg0 <- reg0 ^ reg1
        data class ShiftLeftR(val reg: Register) : Instruction() //                 SAL  reg,  CL     reg  <- reg << CL
        data class ShiftRightR(val reg: Register) : Instruction() //                SAR  reg,  CL     reg  <- reg >> CL

        data class CmpR(val reg0: Register, val reg1: Register) : Instruction() //  CMP   reg0, reg1
        data class SetEqR(val reg: Register) : Instruction() //                     SETE  reg
        data class SetNeqR(val reg: Register) : Instruction() //                    SETNE reg
        data class SetLtR(val reg: Register) : Instruction() //                     SETL  reg
        data class SetLtEqR(val reg: Register) : Instruction() //                   SETLE reg
        data class SetGtR(val reg: Register) : Instruction() //                     SETG  reg
        data class SetGtEqR(val reg: Register) : Instruction() //                   SETGE reg
    }
}
