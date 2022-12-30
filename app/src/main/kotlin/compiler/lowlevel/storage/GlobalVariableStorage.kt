package compiler.lowlevel.storage

import compiler.ast.Program
import java.io.PrintWriter

class GlobalVariableStorage(val program: Program) {
    fun writeAsm(output: PrintWriter) {
        output.println("globals:")
    }
}
