package compiler.parser.analysis.testcases

import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.analysis.GrammarAnalysisTest.DfaFactory
import compiler.parser.analysis.GrammarSymbol
import compiler.parser.grammar.AutomatonGrammar
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCase02 {

    // LINEAR GRAMMAR WITH ALL NULLABLE
    // Grammar:
    //   Sigma = { start, A, B }
    //   Productions = {
    //      start --> A (dfaA)
    //      A --> B (dfaB)
    //      B --> eps (trivial dfa)
    //   }
    //

    private val start = "start"
    private val symA = "A"
    private val symB = "B"

    private val expectedNullable = setOf(
        start,
        symA,
        symB,
    )

    private val expectedFirst = mapOf(
        start to setOf(start, symA, symB),
        symA to setOf(symA, symB),
        symB to setOf(symB),
    )

    private val expectedFollow: Map<GrammarSymbol, Set<GrammarSymbol>> = mapOf(
        start to setOf(),
        symA to setOf(),
        symB to setOf(),
    )

    private val dfaA = DfaFactory.createDfa(
        "startState",
        listOf("startState", "accState"),
        mapOf(
            Pair("startState", symA) to "accState",
        ),
        "A",
    )

    private val dfaB = DfaFactory.createDfa(
        "startState",
        listOf("startState", "accState"),
        mapOf(
            Pair("startState", symB) to "accState",
        ),
        "B",
    )

    private val grammar: AutomatonGrammar<String> = AutomatonGrammar(
        start,
        mapOf(
            start to dfaA,
            symA to dfaB,
            symB to DfaFactory.getTrivialDfa(""),
        )
    )

    @Ignore
    @Test
    fun `test nullable for linear grammar with all nullable`() {
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(grammar)
        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for linear grammar with all nullable`() {
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(grammar, expectedNullable)
        assertEquals(expectedFirst, actualFirst)
    }

    @Ignore
    @Test
    fun `test follow for linear grammar with all nullable`() {
        // In fact, the upper approximation of Follow.
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(grammar, expectedNullable, expectedFirst)
        assertEquals(expectedFollow, actualFollow)
    }
}
