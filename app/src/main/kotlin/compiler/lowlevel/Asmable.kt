package compiler.lowlevel

import compiler.intermediate.Register
import java.io.PrintWriter

sealed interface Asmable {
    // Print in NASM format with the given mapping into hardware registers
    fun writeAsm(output: PrintWriter, registers: Map<Register, Register>)
}
