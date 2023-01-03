package compiler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import compiler.diagnostics.CompilerDiagnostics
import java.io.File
import java.io.FileNotFoundException
import kotlin.system.exitProcess

fun runCommand(command: String) {
    println(command)
    val process = ProcessBuilder(*command.split(" ").toTypedArray())
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    process.waitFor()
    if (process.exitValue() != 0) {
        exitProcess(process.exitValue())
    }
}

class Cli : CliktCommand() {
    private val inputFile: String by argument("input file", help = "Input file")
    private val stdlibLocation: String by option("-l", "--stdlib", help = "Standard library location").default("stdlib.o")
    private val assemblyFile: String by option("-a", "--assembly", help = "Assembly file").default("a.asm")
    private val objectFile: String by option("-b", "--object", help = "Object file").default("a.o")
    private val outputFile: String by option("-o", "--output", help = "Output file").default("a.out")

    override fun run() {
        val diagnostics = CompilerDiagnostics()
        val compiler = Compiler(diagnostics)

        try {
            File(inputFile).reader().use { input ->
                File(assemblyFile).writer().use { output ->
                    if (!compiler.process(input, output)) {
                        println("Compilation failed")
                        diagnostics.diagnostics.forEach { println(it.message) }
                        exitProcess(1)
                    }
                }
            }
        } catch (err: FileNotFoundException) {
            println("File error: ${err.message}")
            exitProcess(1)
        }

        runCommand("nasm -f elf64 $assemblyFile -o $objectFile")
        runCommand("gcc -static $objectFile $stdlibLocation -o $outputFile")
    }
}

fun main(args: Array<String>) = Cli().main(args)
