package compiler.lowlevel.storage

import compiler.ast.Expression
import compiler.ast.Program
import compiler.ast.Variable
import compiler.intermediate.generators.GlobalVariableAccessGenerator
import java.io.PrintWriter
import java.lang.IllegalArgumentException

class GlobalVariableStorage(val program: Program) {

    private fun getValueOfConstExpr(expr: Expression?): Long =
        expr?.let {
            Expression.getValueOfLiteral(it) ?: throw IllegalArgumentException()
        } ?: 0

    fun writeAsm(output: PrintWriter) {
        output.write("section .data\n")
        output.write(GlobalVariableAccessGenerator.GLOBALS_MEMORY_LABEL + ":\n")
        program.globals.filterIsInstance<Program.Global.VariableDefinition>()
            .map { it.variable }
            .filter { it.kind != Variable.Kind.CONSTANT }
            .sortedBy { it.name }
            .forEach {
                output.write("dq 0x%x ; %s\n".format(getValueOfConstExpr(it.value), it.name))
            }
    }
}
