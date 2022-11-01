package compiler.e2e

import compiler.Compiler
import compiler.common.diagnostics.CompilerDiagnostics
import compiler.common.diagnostics.Diagnostic
import kotlin.test.assertContentEquals

object E2eAsserter {
    fun assertProgramGeneratesDiagnostics(program: String, diagnostics: List<Diagnostic>) {
        val actualDiagnostics = CompilerDiagnostics()
        val compiler = Compiler(actualDiagnostics)
        compiler.process(program.trimIndent().reader())
        assertContentEquals(diagnostics.asSequence(), actualDiagnostics.diagnostics)
    }
    fun assertProgramCorrect(program: String) {
        assertProgramGeneratesDiagnostics(program, listOf())
    }
}
