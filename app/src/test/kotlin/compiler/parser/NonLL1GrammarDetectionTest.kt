package compiler.parser

import compiler.grammar.Grammar
import compiler.grammar.Production
import compiler.syntax.utils.LexerRegexParser
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NonLL1GrammarDetectionTest : ParserTestBase() {
    private val aToB = Production('A', LexerRegexParser.parseStringToRegex("B"))
    private val aToC = Production('A', LexerRegexParser.parseStringToRegex("C"))
    private val bToab = Production('B', LexerRegexParser.parseStringToRegex("ab"))
    private val cToac = Production('C', LexerRegexParser.parseStringToRegex("ac"))

    override fun getExpressionGrammar(): Grammar<Char> {
        return Grammar(
            'A',
            listOf(
                aToB,
                aToC,
                bToab,
                cToac,
            ),
        )
    }

    @Test
    fun `test creating parser fails early for non LL1 grammar`() {
        val grammar = getExpressionGrammar()
        assertFailsWith(Parser.AmbiguousParseActions::class) {
            Parser(grammar, getMockedDiagnostics())
        }
    }
}
