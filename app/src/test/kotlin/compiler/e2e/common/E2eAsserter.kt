package compiler.e2e

import compiler.Compiler
import compiler.common.diagnostics.CompilerDiagnostics
import compiler.common.diagnostics.Diagnostic
import kotlin.reflect.KClass
import kotlin.test.assertContentEquals

object E2eAsserter {
    fun <T : Diagnostic> assertProgramGeneratesDiagnostics(program: String, diagnostics: List<Diagnostic>, diagnosticType: KClass<T>) {
        val actualDiagnostics = CompilerDiagnostics()
        val compiler = Compiler(actualDiagnostics)
        try {
            // exceptions will be checked in different tests
            compiler.process(program.trimIndent().reader())
        } catch (e: Throwable) {}
        assertContentEquals(diagnostics.asSequence(), actualDiagnostics.diagnostics.filter { diagnosticType.isInstance(it) })
    }
    fun assertProgramCorrect(program: String) {
        assertProgramGeneratesDiagnostics(program, listOf(), Diagnostic::class)
    }
}
