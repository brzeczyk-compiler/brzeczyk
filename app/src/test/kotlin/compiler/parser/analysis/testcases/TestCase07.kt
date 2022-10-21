package compiler.parser.analysis.testcases

import compiler.parser.analysis.EPSILON
import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.analysis.GrammarAnalysisTest.DfaFactory
import compiler.parser.analysis.GrammarSymbol
import compiler.parser.grammar.AutomatonGrammar
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCase07 {

    // REGEX GRAMMAR
    // Grammar:
    //   Sigma = { start, h, i, j, k }
    //   Productions = {
    //      start --> (h+i)* j + k* (dfaH)
    //   }
    //

    private val start = "start"
    private val symh = "h"
    private val symi = "i"
    private val symj = "j"
    private val symk = "k"

    private val expectedNullable = setOf(
        start,
    )

    private val expectedFirst = mapOf(
        start to setOf(start, symh, symi, symj, symk),
        symh to setOf(symh),
        symi to setOf(symi),
        symj to setOf(symj),
        symk to setOf(symk),
    )

    private val expectedFollow: Map<GrammarSymbol, Set<GrammarSymbol>> = mapOf(
        start to setOf(),
        symh to setOf(symh, symi, symj),
        symi to setOf(symh, symi, symj),
        symj to setOf(),
        symk to setOf(symk),
    )

    private val dfaH = DfaFactory.createDfa(
        "accStartState",
        listOf("accStartState", "state1", "state2", "accState"),
        mapOf(
            Pair("accStartState", EPSILON) to "state1",
            Pair("accStartState", EPSILON) to "state2",
            Pair("state1", symh) to "state1",
            Pair("state1", symi) to "state1",
            Pair("state1", symj) to "accState",
            Pair("state2", symk) to "state2",
            Pair("state2", EPSILON) to "accState",
        ),
    )

    private val grammar: AutomatonGrammar<String> = AutomatonGrammar(
        start,
        mapOf(
            start to dfaH,
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
