package compiler.parser.analysis.testcases

import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.analysis.GrammarAnalysisTest.DfaFactory
import compiler.parser.analysis.GrammarSymbol
import compiler.parser.grammar.AutomatonGrammar
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCase03 {

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
        start,
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
        )
    )

    private val grammar: AutomatonGrammar<String> = AutomatonGrammar(
        start,
        mapOf(
            start to dfaCD,
            symD to DfaFactory.getTrivialDfa(),
        ),
    )

    @Ignore
    @Test
    fun `test nullable for trivial grammar`() {
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(grammar)
        assertEquals(expectedNullable, actualNullable)
    }

    @Ignore
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
