package compiler.lowlevel

import compiler.intermediate.Register
import java.io.PrintWriter

class CodeSection(
    private val mainFunctionLabel: String,
    private val ignoreMainReturnValue: Boolean,
    private val foreignIdentifiers: List<String>,
    val functions: List<FunctionCode>
) {
    data class FunctionCode(val label: String, val instructions: List<Asmable>, val registerAllocation: Map<Register, Register>)

    fun writeAsm(output: PrintWriter) {
        foreignIdentifiers.forEach { output.println("extern $it") }

        output.println(
            """
                global main
                main:
                    push rbp
                    mov rbp, rsp
                    call $mainFunctionLabel
                    ${if (ignoreMainReturnValue) "xor rax, rax" else "" }
                    pop rbp
                    ret

            """.trimIndent()
        )

        functions.forEach { (label, instructions, registerAllocation) ->
            output.println("$label:")
            instructions.forEach {
                output.print("    ")
                it.writeAsm(output, registerAllocation)
                output.println()
            }
            output.println()
        }
    }
}
