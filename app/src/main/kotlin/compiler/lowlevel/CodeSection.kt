package compiler.lowlevel

import compiler.intermediate.Register
import java.io.PrintWriter

class CodeSection(
    val mainFunctionLabel: String,
    val ignoreMainReturnValue: Boolean,
    val foreignFunctionIdentifiers: List<String>,
    val functions: Map<String, FunctionCode>
) {
    data class FunctionCode(val instructions: List<Asmable>, val registerAllocation: Map<Register, Register>)

    fun writeAsm(output: PrintWriter) {
        foreignFunctionIdentifiers.forEach { output.println("extern $it") }

        output.println(
            """
                global main
                main:
                    push rbp
                    mov rbp, rsp
                    call $mainFunctionLabel
                    ${if (ignoreMainReturnValue) "xor rax, rax" else "" }
                    mov rsp, rbp
                    pop rbp
                    ret
                
            """.trimIndent()
        )

        functions.forEach { function ->
            output.println("${function.key}:")
            function.value.instructions.forEach {
                output.print("    ")
                it.writeAsm(output, function.value.registerAllocation)
                output.println()
            }
            output.println()
        }
    }
}
