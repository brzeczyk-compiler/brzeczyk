package compiler.analysis

import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Type

object BuiltinFunctions {
    private val builtinFunctions = mapOf(
        // Writes a single number followed by a new line to output
        "napisz" to Function(
            "napisz",
            listOf(Function.Parameter("wartość", Type.Number, null)),
            Type.Unit,
            Function.Implementation.Foreign("print_int64"),
            false
        ),

        // Prints a unicode string followed by a newline to output
        "wypisz" to Function(
            "wypisz",
            listOf(Function.Parameter("napis", Type.Array(Type.Number), null)),
            Type.Unit,
            Function.Implementation.Foreign("print_string"),
            false
        ),

        // Reads a single number from input
        "wczytaj" to Function(
            "wczytaj",
            emptyList(),
            Type.Number,
            Function.Implementation.Foreign("read_int64"),
            false
        ),

        // Returns consecutive numbers from 0 to `do` - 1
        "przedziału" to Function(
            "przedziału",
            listOf(Function.Parameter("do", Type.Number, null)),
            Type.Number,
            Function.Implementation.Foreign("int64_range"),
            true
        ),

        // Returns numbers read from input until its end
        "wejścia" to Function(
            "wejścia",
            listOf(),
            Type.Number,
            Function.Implementation.Foreign("int64_input"),
            true
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

    val internallyUsedExternalSymbols: List<String> = listOf(
        "_\$checked_malloc",
        "_\$populate_dynamic_array",
        "_\$array_ref_count_decrement",
        "_\$make_array_from_generator",
        "free"
    )
}
