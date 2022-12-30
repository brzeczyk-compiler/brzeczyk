package compiler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import compiler.diagnostics.CompilerDiagnostics
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.PrintWriter

fun runCommand(command: String) {
    println(command)
    ProcessBuilder(*command.split(" ").toTypedArray())
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
}

class Cli : CliktCommand() {
    val inputFile: String by argument("input file", help = "Input file")
    val externFile: String? by option("-e", "--extern", help = "Extern file")
    val assemblyFile: String by option("-a", "--assembly", help = "Assembly file").default("a.asm")
    val listingFile: String? by option("-l", "--listing", help = "Listing file")
    val objectFile: String by option("-b", "--object", help = "Object file").default("a.o")
    val outputFile: String by option("-o", "--output", help = "Output file").default("a.out")

    override fun run() {
        val diagnostics = CompilerDiagnostics()
        val compiler = Compiler(diagnostics)

        try {
            val reader = FileReader(File(inputFile))

            val writer = PrintWriter(File(assemblyFile))

            if (!compiler.process(reader, PrintWriter(writer))) {
                println("Compilation failed")
                diagnostics.diagnostics.forEach { println(it) }
                return
            }

            writer.close()
            reader.close()
        } catch (err: FileNotFoundException) {
            println("Error: ${err.message}")
            return
        }

        runCommand("nasm -f elf64 $assemblyFile${listingFile?.let { " -l $it" } ?: ""} -o $objectFile")
        runCommand("gcc -static $objectFile${externFile?.let {" $it"} ?: ""} -o $outputFile")
    }
}

fun main(args: Array<String>) = Cli().main(args)
