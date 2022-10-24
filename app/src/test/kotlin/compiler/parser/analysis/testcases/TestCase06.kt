package compiler.parser.analysis.testcases

import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.analysis.GrammarAnalysisTest.DfaFactory
import compiler.parser.analysis.GrammarSymbol
import compiler.parser.grammar.AutomatonGrammar
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCase06 {

    // STAR RECURSION GRAMMAR
    // Grammar:
    //   Sigma = { start, g }
    //   Productions = {
    //      start --> g* (dfaG)
    //   }
    //

    private val start = "start"
    private val symg = "g"

    private val expectedNullable = setOf(
        start,
    )

    private val expectedFirst = mapOf(
        start to setOf(start, symg),
        symg to setOf(symg),
    )

    private val expectedFollow: Map<GrammarSymbol, Set<GrammarSymbol>> = mapOf(
        start to setOf(),
        symg to setOf(symg),
    )

    private val dfaG = DfaFactory.createDfa(
        "accStartState",
        listOf("accStartState"),
        mapOf(
            Pair("accStartState", symg) to "accStartState",
        ),
        "G",
    )

    private val grammar: AutomatonGrammar<String> = AutomatonGrammar(
        start,
        mapOf(
            start to dfaG
        ),
    )

    @Test
    fun `test nullable for star recursion grammar`() {
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(grammar)
        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for star recursion grammar`() {
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(grammar, expectedNullable)
        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for star recursion grammar`() {
        // In fact, the upper approximation of Follow.
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(grammar, expectedNullable, expectedFirst)
        assertEquals(expectedFollow, actualFollow)
    }
}
