package compiler.parser.analysis.testcases

import compiler.parser.analysis.EPSILON
import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.analysis.GrammarAnalysisTest.DfaFactory
import compiler.parser.analysis.GrammarSymbol
import compiler.parser.grammar.AutomatonGrammar
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCase12 {

    // TEST FOLLOW GRAMMAR
    //
    // Grammar:
    //   Sigma = { start, u, V, w, x, y, z }
    //   Productions = {
    //      start --> u (V + w)* x + x x (dfaFol)
    //      V --> y z + z* (dfaV)
    //   }
    //

    private val start = "start"
    private val symu = "u"
    private val symV = "V"
    private val symw = "w"
    private val symx = "x"
    private val symy = "y"
    private val symz = "z"

    private val expectedNullable = setOf(
        symV,
    )

    private val expectedFirst = mapOf(
        start to setOf(start, symu, symx),
        symu to setOf(symu),
        symV to setOf(symV, symy, symz),
        symw to setOf(symw),
        symx to setOf(symx),
        symy to setOf(symy),
        symz to setOf(symz),
    )

    private val expectedFollow: Map<GrammarSymbol, Set<GrammarSymbol>> = mapOf(
        start to setOf(),
        symu to setOf(symV, symw, symx, symy, symz),
        symV to setOf(symV, symw, symx, symy, symz),
        symw to setOf(symV, symw, symx, symy, symz),
        symx to setOf(symx),
        symy to setOf(symz),
        symz to setOf(symV, symw, symx, symy, symz),
    )

    private val dfaFol = DfaFactory.createDfa(
        "startState",
        listOf("startState", "state1", "state2", "accState"),
        mapOf(
            Pair("startState", symu) to "state1",
            Pair("state1", symV) to "state1",
            Pair("state1", symw) to "state1",
            Pair("state1", symx) to "accState",
            Pair("startState", symx) to "state2",
            Pair("state2", symx) to "accState",
        ),
    )

    private val dfaV = DfaFactory.createDfa(
        "startState",
        listOf("startState", "state1", "state2", "accState"),
        mapOf(
            Pair("startState", symy) to "state1",
            Pair("state1", symz) to "accState",
            Pair("startState", EPSILON) to "state2",
            Pair("state2", symz) to "state2",
            Pair("state2", EPSILON) to "accState",
        )
    )

    private val grammar: AutomatonGrammar<String> = AutomatonGrammar(
        start,
        mapOf(
            start to dfaFol,
            symV to dfaV,
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
