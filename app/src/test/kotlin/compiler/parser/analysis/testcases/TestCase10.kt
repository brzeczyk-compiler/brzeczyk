package compiler.parser.analysis.testcases

import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.analysis.GrammarAnalysisTest.DfaFactory
import compiler.parser.analysis.GrammarSymbol
import compiler.parser.grammar.AutomatonGrammar
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCase10 {

    // COMPLICATED NON NULLABLE GRAMMAR
    // Grammar:
    //   Sigma = { start, Q, R, s }
    //   Productions = {
    //      start --> Q R R (start) + R Q Q (dfaCNNG)
    //      Q --> s (dfaQ)
    //      R --> QQ + s (dfaR)
    //   }
    //

    private val start = "start"
    private val symQ = "Q"
    private val symR = "R"
    private val syms = "s"

    private val expectedNullable = setOf<GrammarSymbol>()

    private val expectedFirst = mapOf(
        start to setOf(start, symQ, symR, syms),
        symQ to setOf(symQ, syms),
        symR to setOf(symQ, symR, syms),
        syms to setOf(syms),
    )

    private val expectedFollow = mapOf(
        start to setOf(),
        symQ to setOf(start, symQ, symR, syms),
        symR to setOf(start, symQ, symR, syms),
        syms to setOf(start, symQ, symR, syms),
    )

    private val dfaCNNG = DfaFactory.createDfa(
        "startState",
        listOf(
            "startState",
            "state11", "state12", "state13",
            "state21", "state22",
            "accState",
        ),
        mapOf(
            Pair("startState", symQ) to "state11",
            Pair("state11", symR) to "state12",
            Pair("state12", symR) to "state13",
            Pair("state13", start) to "accState",
            Pair("startState", symR) to "state21",
            Pair("state21", symQ) to "state22",
            Pair("state22", symQ) to "accState",
        ),
        "CNNG",
    )

    private val dfaQ = DfaFactory.createDfa(
        "startState",
        listOf("startState", "accState"),
        mapOf(
            Pair("startState", syms) to "accState",
        ),
        "Q",
    )

    private val dfaR = DfaFactory.createDfa(
        "startState",
        listOf("startState", "state1", "accState"),
        mapOf(
            Pair("startState", symQ) to "state1",
            Pair("state1", symQ) to "accState",
            Pair("startState", syms) to "accState",
        ),
        "R",

    )

    private val grammar: AutomatonGrammar<String> = AutomatonGrammar(
        start,
        mapOf(
            start to dfaCNNG,
            symQ to dfaQ,
            symR to dfaR,
        ),
    )

    @Ignore
    @Test
    fun `test nullable for complicated non nullable grammar`() {
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(grammar)
        assertEquals(expectedNullable, actualNullable)
    }

    @Ignore
    @Test
    fun `test first for complicated non nullable grammar`() {
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(grammar, expectedNullable)
        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for complicated non nullable grammar`() {
        // In fact, the upper approximation of Follow.
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(grammar, expectedNullable, expectedFirst)
        assertEquals(expectedFollow, actualFollow)
    }
}
