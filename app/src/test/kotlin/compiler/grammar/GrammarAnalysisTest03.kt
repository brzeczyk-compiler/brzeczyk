package compiler.grammar

import compiler.grammar.GrammarAnalysisTest.DfaFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class GrammarAnalysisTest03 {

    // SIMPLE GRAMMAR WITH NOT ALL NULLABLE
    // Grammar:
    //   Sigma = { start, c, D }
    //   Productions = {
    //      start --> cD (dfaCD)
    //      D --> eps (trivial dfa)
    //   }
    //

    private val start = "start"
    private val symc = "c"
    private val symD = "D"

    private val expectedNullable = setOf(
        symD,
    )

    private val expectedFirst = mapOf(
        start to setOf(start, symc),
        symc to setOf(symc),
        symD to setOf(symD),
    )

    private val expectedFollow: Map<GrammarSymbol, Set<GrammarSymbol>> = mapOf(
        start to setOf(),
        symc to setOf(symD),
        symD to setOf(),
    )

    private val dfaCD = DfaFactory.createDfa(
        "startState",
        listOf("startState", "state1", "accState"),
        mapOf(
            Pair("startState", symc) to "state1",
            Pair("state1", symD) to "accState",
        ),
        "CD",
    )

    private val grammar: AutomatonGrammar<String> = AutomatonGrammar(
        start,
        mapOf(
            start to dfaCD,
            symD to DfaFactory.getTrivialDfa(""),
        ),
    )

    @Test
    fun `test nullable for simple grammar with not all nullable`() {
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(grammar)
        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for simple grammar with not all nullable`() {
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(grammar, expectedNullable)
        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for simple grammar with not all nullable`() {
        // In fact, the upper approximation of Follow.
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(grammar, expectedNullable, expectedFirst)
        assertEquals(expectedFollow, actualFollow)
    }
}
