package compiler.lowlevel.storage

import compiler.ast.Expression
import compiler.ast.Program
import compiler.ast.Type
import compiler.ast.Variable
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class GlobalVariableStorageTest {

    private fun List<Pair<String, Expression?>>.asVarDefList() = this.map {
        Program.Global.VariableDefinition(
            Variable(
                Variable.Kind.VARIABLE,
                it.first,
                Type.Number,
                it.second
            )
        )
    }

    @Test
    fun `global variables asm test`() {
        val definitions = listOf(
            "no expression" to null,
            "bool true" to Expression.BooleanLiteral(true),
            "bool false" to Expression.BooleanLiteral(false),
            "unit" to Expression.UnitLiteral(),
            "number" to Expression.NumberLiteral(12347),
        ).asVarDefList() +
            Program.Global.VariableDefinition(
                Variable(
                    Variable.Kind.CONSTANT,
                    "constant",
                    Type.Number,
                    Expression.UnitLiteral()
                )
            )
        val program = Program(definitions)

        val stringWriter = StringWriter()

        GlobalVariableStorage(program).writeAsm(PrintWriter(stringWriter))

        val expected =
            "globals:\n" +
                "dq 0x0 ; bool false\n" +
                "dq 0x1 ; bool true\n" +
                "dq 0x0 ; no expression\n" +
                "dq 0x303b ; number\n" +
                "dq 0x0 ; unit\n"

        assertEquals(expected, stringWriter.toString())
    }
}
