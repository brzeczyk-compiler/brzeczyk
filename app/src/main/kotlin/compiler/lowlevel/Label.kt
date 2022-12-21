package compiler.lowlevel

import compiler.intermediate.Register
import java.io.PrintWriter

data class Label(val label: String) : Asmable {
    override fun writeAsm(output: PrintWriter, registers: Map<Register, Register>) {
        TODO()
    }
}
