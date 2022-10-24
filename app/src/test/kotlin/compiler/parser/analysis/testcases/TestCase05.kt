package compiler.parser.analysis.testcases

import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.analysis.GrammarAnalysisTest.DfaFactory
import compiler.parser.analysis.GrammarSymbol
import compiler.parser.grammar.AutomatonGrammar
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCase05 {

    // RIGHT RECURSION GRAMMAR
    // Grammar:
    //   Sigma = { start, f }
    //   Productions = {
    //      start --> f(start) + eps (dfaF)
    //   }
    //

    private val start = "start"
    private val symf = "f"

    private val expectedNullable = setOf(
        start,
    )

    private val expectedFirst = mapOf(
        start to setOf(start, symf),
        symf to setOf(symf),
    )

    private val expectedFollow: Map<GrammarSymbol, Set<GrammarSymbol>> = mapOf(
        start to setOf(),
        symf to setOf(start, symf),
    )

    private val dfaF = DfaFactory.createDfa(
        "accStartState",
        listOf("accStartState", "state1", "accState"),
        mapOf(
            Pair("accStartState", symf) to "state1",
            Pair("state1", start) to "accState",
        ),
        "F",
    )

    private val grammar: AutomatonGrammar<String> = AutomatonGrammar(
        start,
        mapOf(
            start to dfaF
        ),
    )

    @Test
    fun `test nullable for right recursion grammar`() {
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(grammar)
        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for right recursion grammar`() {
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(grammar, expectedNullable)
        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for right recursion grammar`() {
        // In fact, the upper approximation of Follow.
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(grammar, expectedNullable, expectedFirst)
        assertEquals(expectedFollow, actualFollow)
    }
}
