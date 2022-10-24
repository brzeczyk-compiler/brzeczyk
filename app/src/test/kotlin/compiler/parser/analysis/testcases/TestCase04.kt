package compiler.parser.analysis.testcases

import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.analysis.GrammarAnalysisTest.DfaFactory
import compiler.parser.analysis.GrammarSymbol
import compiler.parser.grammar.AutomatonGrammar
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCase04 {

    // LEFT RECURSION GRAMMAR
    // Grammar:
    //   Sigma = { start, e }
    //   Productions = {
    //      start --> (start)e + eps (dfaE)
    //   }
    //

    private val start = "start"
    private val syme = "e"

    private val expectedNullable = setOf(
        start,
    )

    private val expectedFirst = mapOf(
        start to setOf(start, syme),
        syme to setOf(syme),
    )

    private val expectedFollow: Map<GrammarSymbol, Set<GrammarSymbol>> = mapOf(
        start to setOf(syme),
        syme to setOf(syme),
    )

    private val dfaE = DfaFactory.createDfa(
        "accStartState",
        listOf("accStartState", "state1", "accState"),
        mapOf(
            Pair("accStartState", start) to "state1",
            Pair("state1", syme) to "accState",
        ),
        "E",
    )

    private val grammar: AutomatonGrammar<String> = AutomatonGrammar(
        start,
        mapOf(
            start to dfaE
        ),
    )

    @Test
    fun `test nullable for left recursion grammar`() {
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(grammar)
        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for left recursion grammar`() {
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(grammar, expectedNullable)
        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for left recursion grammar`() {
        // In fact, the upper approximation of Follow.
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(grammar, expectedNullable, expectedFirst)
        assertEquals(expectedFollow, actualFollow)
    }
}
