package compiler.lowlevel.storage

import java.io.PrintWriter

class DisplayStorage(private val programStaticDepth: Int) {

    val displayLabel: String = "display"

    fun writeAsm(output: PrintWriter) {
        output.write("$displayLabel: resq $programStaticDepth")
    }
}
