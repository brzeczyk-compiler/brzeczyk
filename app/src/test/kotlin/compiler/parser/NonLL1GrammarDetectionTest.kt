package compiler.parser

import compiler.grammar.Grammar
import compiler.grammar.Production
import compiler.syntax.utils.TokenRegexParser
import kotlin.test.Test
import kotlin.test.assertFailsWith

class NonLL1GrammarDetectionTest : ParserTestBase() {
    private val aToB = Production('A', TokenRegexParser.parseStringToRegex("B"))
    private val aToC = Production('A', TokenRegexParser.parseStringToRegex("C"))
    private val bToab = Production('B', TokenRegexParser.parseStringToRegex("ab"))
    private val cToac = Production('C', TokenRegexParser.parseStringToRegex("ac"))

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
