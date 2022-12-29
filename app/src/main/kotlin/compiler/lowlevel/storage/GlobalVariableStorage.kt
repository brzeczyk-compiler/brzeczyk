package compiler.lowlevel.storage

import compiler.ast.Expression
import compiler.ast.Program
import compiler.intermediate.generators.GlobalVariableAccessGenerator
import java.io.PrintWriter
import java.lang.IllegalArgumentException

class GlobalVariableStorage(val program: Program) {

    private fun getValueOfConstExpr(expr: Expression?): Long = when (expr) {
        null -> 0
        is Expression.UnitLiteral -> 0
        is Expression.BooleanLiteral -> if (expr.value) 1 else 0
        is Expression.NumberLiteral -> expr.value
        else -> throw IllegalArgumentException()
    }

    fun writeAsm(output: PrintWriter) {
        output.write("section .data\n")
        output.write(GlobalVariableAccessGenerator.GLOBALS_MEMORY_LABEL + ":\n")
        program.globals.filterIsInstance<Program.Global.VariableDefinition>()
            .map { it.variable }
            .sortedBy { it.name }
            .forEach {
                output.write("dq 0x%x ; %s\n".format(getValueOfConstExpr(it.value), it.name))
            }
    }
}
