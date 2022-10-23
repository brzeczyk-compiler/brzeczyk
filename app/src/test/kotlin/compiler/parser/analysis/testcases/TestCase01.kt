package compiler.parser.analysis.testcases

import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.analysis.GrammarAnalysisTest.DfaFactory
import compiler.parser.analysis.GrammarSymbol
import compiler.parser.grammar.AutomatonGrammar
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCase01 {

    // TRIVIAL GRAMMAR
    // Grammar:
    //   Sigma = { start }
    //   Productions = {
    //      start --> eps (trivial dfa)
    //   }
    //

    private val start = "start"

    private val expectedNullable = setOf(
        start,
    )

    private val expectedFirst = mapOf(
        start to setOf(start),
    )

    private val expectedFollow = mapOf(
        start to setOf<GrammarSymbol>(),
    )

    private val grammar: AutomatonGrammar<GrammarSymbol> = AutomatonGrammar(
        start,
        mapOf(
            start to DfaFactory.getTrivialDfa("")
        ),
    )

    @Ignore
    @Test
    fun `test nullable for trivial grammar`() {
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(grammar)
        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for trivial grammar`() {
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(grammar, expectedNullable)
        assertEquals(expectedFirst, actualFirst)
    }

    @Ignore
    @Test
    fun `test follow for trivial grammar`() {
        // In fact, the upper approximation of Follow.
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(grammar, expectedNullable, expectedFirst)
        assertEquals(expectedFollow, actualFollow)
    }
}
