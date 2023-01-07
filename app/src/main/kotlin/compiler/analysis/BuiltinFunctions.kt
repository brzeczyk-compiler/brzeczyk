package compiler.analysis

import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Type

object BuiltinFunctions {
    private val builtinFunctions = mapOf(
        "napisz" to Function(
            "napisz",
            listOf(Function.Parameter("wartość", Type.Number, null)),
            Type.Unit,
            Function.Implementation.Foreign("print_int64"),
            false
        ),
        "wczytaj" to Function(
            "wczytaj",
            emptyList(),
            Type.Number,
            Function.Implementation.Foreign("read_int64"),
            false
        )
    )

    fun addBuiltinFunctions(program: Program): Program {
        val globalNames = program.globals.map {
            when (it) {
                is Program.Global.FunctionDefinition -> it.function.name
                is Program.Global.VariableDefinition -> it.variable.name
            }
        }.toSet()
        return Program(builtinFunctions.filter { it.key !in globalNames }.map { Program.Global.FunctionDefinition(it.value) } + program.globals)
    }

    val internallyUsedExternalSymbols: List<String> = listOf("\$checked_malloc", "free")
}
