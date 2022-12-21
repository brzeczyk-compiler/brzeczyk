package compiler.grammar

import compiler.grammar.GrammarAnalysisTest.DfaFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class GrammarAnalysisTest01 {

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

    @Test
    fun `test follow for trivial grammar`() {
        // In fact, the upper approximation of Follow.
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(grammar, expectedNullable, expectedFirst)
        assertEquals(expectedFollow, actualFollow)
    }
}
