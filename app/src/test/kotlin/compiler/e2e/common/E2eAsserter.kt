package compiler.e2e.common

import compiler.Compiler
import compiler.common.diagnostics.CompilerDiagnostics
import compiler.common.diagnostics.Diagnostic
import kotlin.reflect.KClass
import kotlin.test.assertContentEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

object E2eAsserter {
    val diagnostics = CompilerDiagnostics()
    val compiler = Compiler(diagnostics)

    private fun runProgram(program: String): Sequence<Diagnostic> {
        diagnostics.clear()
        compiler.process(program.trimIndent().reader())
        return diagnostics.diagnostics
    }

    fun <T : Diagnostic> assertProgramGeneratesDiagnostics(program: String, diagnostics: List<Diagnostic>, diagnosticType: KClass<T>) {
        val actualDiagnostics = runProgram(program)
        assertContentEquals(diagnostics.asSequence(), actualDiagnostics.filter { diagnosticType.isInstance(it) })
    }

    fun <T : Diagnostic> assertErrorOfType(program: String, errorType: KClass<T>) {
        val actualDiagnostics = runProgram(program)
        val actualDiagnosticsOfType = actualDiagnostics.filter { errorType.isInstance(it) }.toList()
        assertNotEquals(0, actualDiagnosticsOfType.size, "Found 0 diagnostics (expected > 0) for program:\n$program")
        assertTrue(actualDiagnosticsOfType.map { it.isError() }.any(), "Found 0 errors (expected > 0) for program:\n$program")
    }

    fun assertProgramCorrect(program: String) {
        assertProgramGeneratesDiagnostics(program, listOf(), Diagnostic::class)
    }
}
