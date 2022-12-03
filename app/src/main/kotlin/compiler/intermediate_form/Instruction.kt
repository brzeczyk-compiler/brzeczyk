package compiler.intermediate_form

sealed class Instruction {
    data class MoveRR(val reg0: Register, val reg1: Register) : Instruction() //     MOV  reg0, reg1
    data class MoveRM(val reg: Register, val address: Addressing) : Instruction() // MOV  reg,  mem
    data class MoveMR(val address: Addressing, val reg: Register) : Instruction() // MOV  mem,  reg
    data class MoveRI(val reg: Register, val constant: Long) : Instruction() //      MOV  reg,  imm
    data class Lea(val reg: Register, val address: Addressing) : Instruction() //    LEA  reg,  mem

    data class PushR(val reg: Register) : Instruction() //                    PUSH reg
    data class PushM(val address: Addressing) : Instruction() //              PUSH mem
    data class PushI(val address: Addressing) : Instruction() //              PUSH imm
    data class PopR(val reg: Register) : Instruction() //                     POP  reg
    data class PopM(val address: Addressing) : Instruction() //               POP  mem

    data class Add(val reg0: Register, val reg1: Register) : Instruction() // ADD  reg0, reg1   reg0 + reg1   -> reg0
    data class Sub(val reg0: Register, val reg1: Register) : Instruction() // SUB  reg0, reg1   reg0 - reg1   -> reg0
    data class Mul(val reg: Register) : Instruction() //                      IMUL reg          RAX     * reg -> RDX:RAX
    data class Div(val reg: Register) : Instruction() //                      IDIV reg          RDX:RAX / reg -> RAX
    //                                                                                          RDX:RAX % reg -> RDX

    data class BitNeg(val reg0: Register) : Instruction() //                  NEG  reg          ~reg        -> reg
    data class And(val reg0: Register, val reg1: Register) : Instruction() // AND  reg0, reg1   reg0 & reg1 -> reg0
    data class Or(val reg0: Register, val reg1: Register) : Instruction() //  OR   reg0, reg1   reg0 & reg1 -> reg0
    data class Xor(val reg0: Register, val reg1: Register) : Instruction() // XOR  reg0, reg1   reg0 ^ reg1 -> reg0
    data class ShiftLeft(val reg: Register) : Instruction() //                SHL  reg,  CL     reg << CL   -> reg
    data class ShiftRight(val reg: Register) : Instruction() //               SHR  reg,  CL     reg >> CL   -> reg

    data class Cmp(val reg0: Register, val reg1: Register) : Instruction() // CMP   reg0, reg1
    data class SetEq(val reg: Register) : Instruction() //                    SETE  reg
    data class SetNeq(val reg: Register) : Instruction() //                   SETNE reg
    data class SetLt(val reg: Register) : Instruction() //                    SETL  reg
    data class SetLteq(val reg: Register) : Instruction() //                  SETLE reg
    data class SetGt(val reg: Register) : Instruction() //                    SETG  reg
    data class SetGteq(val reg: Register) : Instruction() //                  SETGE reg

    data class CallR(val reg: Register) : Instruction() //                    CALL reg
    data class CallM(val address: Addressing) : Instruction() //              CALL mem
    class Ret : Instruction() //                                              RET
}
