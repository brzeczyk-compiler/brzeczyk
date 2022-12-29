package compiler.lowlevel.storage

import java.io.PrintWriter

const val DISPLAY_LABEL_IN_MEMORY = "display"

class DisplayStorage(private val programStaticDepth: Int) {

    fun writeAsm(output: PrintWriter) {
        output.write("$DISPLAY_LABEL_IN_MEMORY: resq $programStaticDepth")
    }
}
