package compiler.lowlevel

import java.io.PrintWriter

object AsmFile {
    fun printFile(
        output: PrintWriter,
        bssSection: (PrintWriter) -> Unit,
        dataSection: (PrintWriter) -> Unit,
        textSection: (PrintWriter) -> Unit
    ) {
        output.println("SECTION .bss")
        bssSection(output)

        output.println("SECTION .data")
        dataSection(output)
        output.println()

        output.println("SECTION .text")
        textSection(output)
    }
}
