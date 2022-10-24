package compiler.parser.analysis.testcases

import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.analysis.GrammarAnalysisTest.DfaFactory
import compiler.parser.analysis.GrammarSymbol
import compiler.parser.grammar.AutomatonGrammar
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCase13 {

    // TEST DFA WITH CYCLES GRAMMAR
    //
    // Grammar:
    //   Sigma = { start, a, B, c, D }
    //   Productions = {
    //      start --> a (a B c)* B (dfaSt)
    //      B --> D (dfaB)
    //      D --> eps (trivial dfa)
    //   }
    //

    private val start = "start"
    private val syma = "a"
    private val symB = "B"
    private val symc = "c"
    private val symD = "D"

    private val expectedNullable = setOf(
        symB,
        symD,
    )

    private val expectedFirst = mapOf(
        start to setOf(start, syma),
        syma to setOf(syma),
        symB to setOf(symB, symD),
        symc to setOf(symc),
        symD to setOf(symD),
    )

    private val expectedFollow = mapOf(
        start to setOf(),
        syma to setOf(syma, symB, symc, symD),
        symB to setOf(symc),
        symc to setOf(syma, symB, symD),
        symD to setOf(symc),
    )

    private val dfaSt = DfaFactory.createDfa(
        "startState",
        listOf("startState", "state1", "state2", "state3", "accState"),
        mapOf(
            Pair("startState", syma) to "state1",
            Pair("state1", syma) to "state2",
            Pair("state2", symB) to "state3",
            Pair("state3", symc) to "state1",
            Pair("state1", symB) to "accState",
        ),
        "St",
    )

    private val dfaB = DfaFactory.createDfa(
        "startState",
        listOf("startState", "accState"),
        mapOf(
            Pair("startState", symD) to "accState",
        ),
        "B",
    )

    private val grammar: AutomatonGrammar<String> = AutomatonGrammar(
        start,
        mapOf(
            start to dfaSt,
            symB to dfaB,
            symD to DfaFactory.getTrivialDfa(""),
        ),
    )

    @Test
    fun `test nullable for dfa with cycles grammar`() {
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(grammar)
        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for dfa with cycles grammar`() {
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(grammar, expectedNullable)
        assertEquals(expectedFirst, actualFirst)
    }

    @Ignore
    @Test
    fun `test follow for dfa with cycles grammar`() {
        // In fact, the upper approximation of Follow.
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(grammar, expectedNullable, expectedFirst)
        assertEquals(expectedFollow, actualFollow)
    }
}
