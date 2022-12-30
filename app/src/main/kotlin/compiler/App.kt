package compiler

import compiler.diagnostics.CompilerDiagnostics
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter

fun main() {
    val diagnostics = CompilerDiagnostics()
    val compiler = Compiler(diagnostics)

    val program = """
        czynność główna() {
            zm n : Liczba = 3
            n = n + 1
        }
    """
    val reader = StringReader(program)
    val writer = PrintWriter(StringWriter())

    compiler.process(reader, writer)
    // TODO:
    // - create Reader for input source file
    // - create Writer for output assembly file
    // - call Compiler.process()
    // - report any diagnostics
    // - execute NASM on output assembly file
    // - execute GCC or LD on resulting object file
}
