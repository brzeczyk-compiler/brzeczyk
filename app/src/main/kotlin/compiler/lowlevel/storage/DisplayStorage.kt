package compiler.lowlevel.storage

import compiler.intermediate.generators.DISPLAY_LABEL_IN_MEMORY
import java.io.PrintWriter

class DisplayStorage(private val programStaticDepth: Int) {

    fun writeAsm(output: PrintWriter) {
        output.write("$DISPLAY_LABEL_IN_MEMORY: resq $programStaticDepth")
    }
}
