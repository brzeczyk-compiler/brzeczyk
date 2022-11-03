package compiler.parser.integration_tests

import compiler.lexer.lexer_grammar.RegexParser
import compiler.parser.Parser
import compiler.parser.grammar.Grammar
import compiler.parser.grammar.Production
import org.junit.Test
import kotlin.test.Ignore
import kotlin.test.assertFails

class NonLL1Grammar : ParserTest() {
    private val aToaB = Production('A', RegexParser.parseStringToRegex("aB"))
    private val aToaC = Production('A', RegexParser.parseStringToRegex("aC"))
    private val bTob = Production('B', RegexParser.parseStringToRegex("b"))
    private val cToc = Production('C', RegexParser.parseStringToRegex("c"))
    private val aTox = Production('A', RegexParser.parseStringToRegex("x"))

    override fun getExpressionGrammar(): Grammar<Char> {
        return Grammar(
            'A',
            listOf(
                aToaB,
                aToaC,
                bTob,
                cToc,
                aTox
            ),
        )
    }

    @Ignore @Test
    fun `test creating parser fails early for non LL1 grammar`() {
        val grammar = getExpressionGrammar()
        assertFails { Parser(grammar, getMockedDiagnostics()) }
    }
}
