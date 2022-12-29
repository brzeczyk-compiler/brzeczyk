package compiler.lowlevel.storage

import compiler.ast.Program
import java.io.PrintWriter

class DisplayStorage(val program: Program) {
    fun writeAsm(output: PrintWriter) {
        output.write("extern malloc")
        output.write("mov rdi, " + program.staticFunctionDepth)
        output.write("call malloc")
        // result address lies in rax
    }
}
