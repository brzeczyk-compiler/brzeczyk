package compiler.parser.grammar

import compiler.common.diagnostics.Diagnostics
import compiler.parser.Parser
import io.mockk.mockk
import kotlin.test.Test

class ParserGrammarTest {
    @Test fun `test grammar regexes are correct syntactically`() {
        ParserGrammar.getGrammar()
    }

    @Test fun `test grammar is accepted by the parser`() {
        val grammar = ParserGrammar.getGrammar()
        val dummyDiagnostics = mockk<Diagnostics>()

        Parser<Symbol>(grammar, dummyDiagnostics)
    }
}
