package compiler.syntax

import compiler.diagnostics.Diagnostics
import compiler.parser.Parser
import io.mockk.mockk
import kotlin.test.Test

class LanguageGrammarTest {
    @Test fun `test grammar regexes are correct syntactically`() {
        LanguageGrammar.getGrammar()
    }

    @Test fun `test grammar is accepted by the parser`() {
        val grammar = LanguageGrammar.getGrammar()
        val dummyDiagnostics = mockk<Diagnostics>()

        Parser(grammar, dummyDiagnostics)
    }
}
