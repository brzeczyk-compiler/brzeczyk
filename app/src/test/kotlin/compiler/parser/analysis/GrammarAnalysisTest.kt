package compiler.parser.analysis

import compiler.common.dfa.DfaWalk
import compiler.common.dfa.state_dfa.DfaState
import compiler.common.dfa.state_dfa.Dfa
import compiler.parser.grammar.AutomatonGrammar
import compiler.parser.grammar.Production
import kotlin.test.Test
import kotlin.test.assertEquals

typealias R = Production<GrammarSymbol>
typealias GrammarSymbol = String
const val EPSILON = ""

class GrammarAnalysisTest {

    class TestDfa(
            override val startState: TestDfaState,
            private val dfaDescription: Map<Pair<TestDfaState, GrammarSymbol>, TestDfaState>
    ) : Dfa<GrammarSymbol, R> {

        override fun newWalk(): DfaWalk<GrammarSymbol, R> = TestDfaWalk(startState, dfaDescription)

    }

    class TestDfaWalk(
            private var currentState: TestDfaState,
            private val dfaDescription: Map<Pair<TestDfaState, GrammarSymbol>, TestDfaState>
    ) : DfaWalk<GrammarSymbol, R> {

        override fun getAcceptingStateTypeOrNull(): R? = if (currentState.isAccepting()) R() else null

        override fun isDead(): Boolean = !dfaDescription.keys.map { it.first }.contains(currentState)

        override fun step(a: GrammarSymbol) {
            dfaDescription[Pair(currentState, a)]?.let { currentState = it }
        }

    }

    class TestDfaState(private val name: String) : DfaState<GrammarSymbol, R> {

        override val result: R = R()
        override val possibleSteps: Map<GrammarSymbol, TestDfaState> = mapOf()

        fun isAccepting(): Boolean = name.startsWith("acc")

    }

    // Trivial Dfa is a Dfa that accepts { eps }.
    private fun getTrivialDfa(): TestDfa = TestDfa(TestDfaState("accStartDfa"), mapOf())

    // Start nonterminal symbol for all grammars
    private val start = "startState"


    // TRIVIAL GRAMMAR
    // Grammar:
    //   Sigma = { start }
    //   Productions = {
    //      start --> eps (trivial dfa)
    //   }
    //
    //      Nullable = { start }
    //      First(start) = { start }
    //      Follow(start) = { }
    //

    private val trivialGrammar: AutomatonGrammar<GrammarSymbol> = AutomatonGrammar(start, mapOf(start to getTrivialDfa()))

    @Test
    fun `test nullable for trivial grammar`() {
        val expectedNullable = setOf(start)
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(trivialGrammar)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for trivial grammar`() {
        val nullable = setOf(start)

        val expectedFirst = mapOf(start to setOf(start))
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(trivialGrammar, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for trivial grammar`() {
        val nullable = setOf(start)
        val first = mapOf(start to setOf(start))

        val expectedFollow = mapOf(start to setOf<GrammarSymbol>())
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(trivialGrammar, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }


    // LINEAR GRAMMAR WITH ALL NULLABLE
    // Grammar:
    //   Sigma = { start, A, B }
    //   Productions = {
    //      start --> A (dfaA)
    //      A --> B (dfaB)
    //      B --> eps (trivial dfa)
    //   }
    //
    //      Nullable = { start, A, B }
    //
    //      First(start) = { start, A, B }
    //      Follow(start) = { }
    //
    //      First(A) = { A, B }
    //      Follow(A) = { }
    //
    //      First(A) = { B }
    //      Follow(A) = { }
    //

    private val symA = "A"
    private val symB = "B"

    private val dfaStatesA = listOf("startState", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionA = mapOf(
            Pair(dfaStatesA["startState"]!!, symA) to dfaStatesA["accState"]!!
    )

    private val dfaStatesB = listOf("startState", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionB = mapOf(
            Pair(dfaStatesB["startState"]!!, symB) to dfaStatesB["accState"]!!
    )

    private val dfaA = TestDfa(dfaStatesA["startState"]!!, dfaDescriptionA)
    private val dfaB = TestDfa(dfaStatesB["startState"]!!, dfaDescriptionB)

    private val linearNullableGrammar: AutomatonGrammar<String> = AutomatonGrammar(
            start,
            mapOf(
                    start to dfaA,
                    symA to dfaB,
                    symB to getTrivialDfa()
            )
    )

    @Test
    fun `test nullable for linear grammar with all nullable`() {
        val expectedNullable = setOf(start, symA, symB)
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(linearNullableGrammar)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for linear grammar with all nullable`() {
        val nullable = setOf(start, symA, symB)

        val expectedFirst = mapOf(
                start to setOf(start, symA, symB),
                symA to setOf(symA, symB),
                symB to setOf(symB)
        )
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(linearNullableGrammar, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for linear grammar with all nullable`() {
        val nullable = setOf(start, symA, symB)
        val first = mapOf(
                start to setOf(start, symA, symB),
                symA to setOf(symA, symB),
                symB to setOf(symB)
        )

        val expectedFollow = mapOf<GrammarSymbol, Set<GrammarSymbol>>(
                start to setOf(),
                symA to setOf(),
                symB to setOf(),
        )
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(linearNullableGrammar, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }



    // SIMPLE GRAMMAR WITH NOT ALL NULLABLE
    // Grammar:
    //   Sigma = { start, c, D }
    //   Productions = {
    //      start --> cD (dfaCD)
    //      D --> eps (trivial dfa)
    //   }
    //
    //      Nullable = { D }
    //
    //      First(start) = { start, c }
    //      Follow(start) = { }
    //
    //      First(c) = { c }
    //      Follow(c) = { D }
    //
    //      First(D) = { D }
    //      Follow(D) = { }
    //

    private val symc = "c"
    private val symD = "D"

    private val dfaStatesCD = listOf("startState", "state1", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionCD = mapOf(
            Pair(dfaStatesCD["startState"]!!, symc) to dfaStatesCD["state1"]!!,
            Pair(dfaStatesCD["state1"]!!, symD) to dfaStatesCD["accState"]!!
    )

    private val dfaCD = TestDfa(dfaStatesCD["startState"]!!, dfaDescriptionCD)

    private val simpleNotNullableGrammar: AutomatonGrammar<String> = AutomatonGrammar(
            start,
            mapOf(
                    start to dfaCD,
                    symD to getTrivialDfa()
            )
    )

    @Test
    fun `test nullable for simple grammar with not all nullable`() {
        val expectedNullable = setOf(start, symc)
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(simpleNotNullableGrammar)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for simple grammar with not all nullable`() {
        val nullable = setOf(start, symc)

        val expectedFirst = mapOf(
                start to setOf(start, symc),
                symc to setOf(symc),
                symD to setOf(symD)
        )
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(simpleNotNullableGrammar, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for simple grammar with not all nullable`() {
        val nullable = setOf(start, symc)
        val first = mapOf(
                start to setOf(start, symc),
                symc to setOf(symc),
                symD to setOf(symD)
        )

        val expectedFollow = mapOf(
                start to setOf(),
                symc to setOf(symD),
                symD to setOf(),
        )
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(simpleNotNullableGrammar, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }



    // LEFT RECURSION GRAMMAR
    // Grammar:
    //   Sigma = { start, e }
    //   Productions = {
    //      start --> (start)e (dfaE)
    //      start --> eps (trivial dfa)
    //   }
    //
    //      Nullable = { start }
    //
    //      First(start) = { start }
    //      Follow(start) = { e }
    //
    //      First(e) = { e }
    //      Follow(e) = { e }
    //

    private val syme = "e"

    private val dfaStatesE = listOf("startState", "state1", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionE = mapOf(
            Pair(dfaStatesE["startState"]!!, start) to dfaStatesE["state1"]!!,
            Pair(dfaStatesE["state1"]!!, syme) to dfaStatesE["accState"]!!,
            Pair(dfaStatesE["startState"]!!, EPSILON) to dfaStatesE["accState"]!!,
    )

    private val dfaE = TestDfa(dfaStatesE["startState"]!!, dfaDescriptionE)

    private val leftRecursionGrammar: AutomatonGrammar<String> = AutomatonGrammar(
            start,
            mapOf(
                    start to dfaE
            )
    )

    @Test
    fun `test nullable for left recursion grammar`() {
        val expectedNullable = setOf(start)
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(leftRecursionGrammar)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for left recursion grammar`() {
        val nullable = setOf(start)

        val expectedFirst = mapOf(
                start to setOf(start),
                syme to setOf(syme)
        )
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(leftRecursionGrammar, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for left recursion grammar`() {
        val nullable = setOf(start)
        val first = mapOf(
                start to setOf(start),
                syme to setOf(syme)
        )

        val expectedFollow = mapOf(
                start to setOf(syme),
                syme to setOf(syme)
        )
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(leftRecursionGrammar, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }



    // RIGHT RECURSION GRAMMAR
    // Grammar:
    //   Sigma = { start, f }
    //   Productions = {
    //      start --> f(start) (dfaF)
    //      start --> eps (trivial dfa)
    //   }
    //
    //      Nullable = { start }
    //
    //      First(start) = { start, f }
    //      Follow(start) = { }
    //
    //      First(f) = { f }
    //      Follow(f) = { f, start }
    //

    private val symf = "f"

    private val dfaStatesF = listOf("startState", "state1", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionF = mapOf(
            Pair(dfaStatesF["startState"]!!, symf) to dfaStatesF["state1"]!!,
            Pair(dfaStatesF["state1"]!!, start) to dfaStatesF["accState"]!!,
            Pair(dfaStatesE["startState"]!!, EPSILON) to dfaStatesE["accState"]!!
    )

    private val dfaF = TestDfa(dfaStatesF["startState"]!!, dfaDescriptionF)

    private val rightRecursionGrammar: AutomatonGrammar<String> = AutomatonGrammar(
            start,
            mapOf(
                    start to dfaF
            )
    )

    @Test
    fun `test nullable for right recursion grammar`() {
        val expectedNullable = setOf(start)
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(rightRecursionGrammar)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for right recursion grammar`() {
        val nullable = setOf(start)

        val expectedFirst = mapOf(
                start to setOf(start, symf),
                symf to setOf(symf)
        )
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(rightRecursionGrammar, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for right recursion grammar`() {
        val nullable = setOf(start)
        val first = mapOf(
                start to setOf(start, symf),
                symf to setOf(symf)
        )

        val expectedFollow = mapOf(
                start to setOf(),
                symf to setOf(start, symf)
        )
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(rightRecursionGrammar, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }



    // STAR RECURSION GRAMMAR
    // Grammar:
    //   Sigma = { start, g }
    //   Productions = {
    //      start --> g* (dfaG)
    //   }
    //
    //      Nullable = { start }
    //
    //      First(start) = { start, g }
    //      Follow(start) = { }
    //
    //      First(g) = { g }
    //      Follow(g) = { g }
    //

    private val symg = "g"

    private val dfaStatesG = listOf("accStartState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionG = mapOf(
            Pair(dfaStatesG["accStartState"]!!, symg) to dfaStatesG["accStartState"]!!
    )

    private val dfaG = TestDfa(dfaStatesG["accStartState"]!!, dfaDescriptionG)

    private val starRecursionGrammar: AutomatonGrammar<String> = AutomatonGrammar(
            start,
            mapOf(
                    start to dfaG
            )
    )

    @Test
    fun `test nullable for star recursion grammar`() {
        val expectedNullable = setOf(start)
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(starRecursionGrammar)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for star recursion grammar`() {
        val nullable = setOf(start)

        val expectedFirst = mapOf(
                start to setOf(start, symg),
                symg to setOf(symg)
        )
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(starRecursionGrammar, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for star recursion grammar`() {
        val nullable = setOf(start)
        val first = mapOf(
                start to setOf(start, symg),
                symg to setOf(symg)
        )

        val expectedFollow = mapOf(
                start to setOf(),
                symg to setOf(symg)
        )
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(starRecursionGrammar, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }



    // REGEX GRAMMAR
    // Grammar:
    //   Sigma = { start, h, i, j, k }
    //   Productions = {
    //      start --> (h+i)* j + k* (dfaH)
    //   }
    //
    //      Nullable = { start }
    //
    //      First(start) = { start, h, i, j, k }
    //      Follow(start) = { }
    //
    //      for every x \in {h,i,j,k}
    //      First(x) = { x }
    //
    //      Follow(h) = { h, i, j }
    //      Follow(i) = { h, i, j }
    //      Follow(j) = { }
    //      Follow(k) = { k }
    //

    private val symh = "h"
    private val symi = "i"
    private val symj = "j"
    private val symk = "k"

    private val dfaStatesH = listOf("accStartState", "state1", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionH = mapOf(
            Pair(dfaStatesH["accStartState"]!!, symk) to dfaStatesH["accStartState"]!!,
            Pair(dfaStatesH["accStartState"]!!, EPSILON) to dfaStatesH["state1"]!!,
            Pair(dfaStatesH["state1"]!!, symh) to dfaStatesH["state1"]!!,
            Pair(dfaStatesH["state1"]!!, symi) to dfaStatesH["state1"]!!,
            Pair(dfaStatesH["state1"]!!, symj) to dfaStatesH["accState"]!!
    )

    private val dfaH = TestDfa(dfaStatesH["accStartState"]!!, dfaDescriptionH)

    private val regexGrammar: AutomatonGrammar<String> = AutomatonGrammar(
            start,
            mapOf(
                    start to dfaH
            )
    )

    @Test
    fun `test nullable for regex grammar`() {
        val expectedNullable = setOf(start)
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(regexGrammar)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for regex grammar`() {
        val nullable = setOf(start)

        val expectedFirst = mapOf(
                start to setOf(start, symh, symi, symj, symk),
                symh to setOf(symh),
                symi to setOf(symi),
                symj to setOf(symj),
                symk to setOf(symk)
        )
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(regexGrammar, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for regex grammar`() {
        val nullable = setOf(start)
        val first = mapOf(
                start to setOf(start, symh, symi, symj, symk),
                symh to setOf(symh),
                symi to setOf(symi),
                symj to setOf(symj),
                symk to setOf(symk)
        )

        val expectedFollow = mapOf(
                start to setOf(),
                symh to setOf(symh, symi, symj),
                symi to setOf(symh, symi, symj),
                symj to setOf(),
                symk to setOf(symk)
        )
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(regexGrammar, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }



    // COMPLICATED NULLABLE GRAMMAR
    // Grammar:
    //   Sigma = { start, L, M, N, o }
    //   Productions = {
    //      start --> L M M N N N (start) + N M M L L L (dfaCNG)
    //      L --> eps (trivial dfa)
    //      M --> o* (dfaM)
    //      N --> o + eps (dfaN)
    //   }
    //
    //      Nullable = { start, L, M, N }
    //
    //      First(start) = { start, L, M, N, o }
    //      Follow(start) = { }
    //
    //      First(L) = { L }
    //      First(M) = { M, o }
    //      First(N) = { N, o }
    //      First(o) = { o }
    //
    //      Follow(L) = { start, L, M, N, o }
    //      Follow(M) = { start, L, M, N, o }
    //      Follow(N) = { start, L, M, N, o }
    //      Follow(o) = { start, L, M, N, o }
    //

    private val symL = "L"
    private val symM = "M"
    private val symN = "N"
    private val symo = "o"

    private val dfaStatesCNG = listOf(
            "startState",
            "state11", "state12", "state13", "state14", "state15", "state16",
            "state21", "state22", "state23", "state24", "state25",
            "accState"
    ).map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionCNG = mapOf(
            Pair(dfaStatesCNG["startState"]!!, symL) to dfaStatesCNG["state11"]!!,
            Pair(dfaStatesCNG["state11"]!!, symM) to dfaStatesCNG["state12"]!!,
            Pair(dfaStatesCNG["state12"]!!, symM) to dfaStatesCNG["state13"]!!,
            Pair(dfaStatesCNG["state13"]!!, symN) to dfaStatesCNG["state14"]!!,
            Pair(dfaStatesCNG["state14"]!!, symN) to dfaStatesCNG["state15"]!!,
            Pair(dfaStatesCNG["state15"]!!, symN) to dfaStatesCNG["state16"]!!,
            Pair(dfaStatesCNG["state16"]!!, start) to dfaStatesCNG["accState"]!!,
            Pair(dfaStatesCNG["startState"]!!, symN) to dfaStatesCNG["state21"]!!,
            Pair(dfaStatesCNG["state21"]!!, symM) to dfaStatesCNG["state22"]!!,
            Pair(dfaStatesCNG["state22"]!!, symM) to dfaStatesCNG["state23"]!!,
            Pair(dfaStatesCNG["state23"]!!, symL) to dfaStatesCNG["state24"]!!,
            Pair(dfaStatesCNG["state24"]!!, symL) to dfaStatesCNG["state25"]!!,
            Pair(dfaStatesCNG["state25"]!!, symL) to dfaStatesCNG["accState"]!!
    )


    private val dfaStatesM = listOf("accStartState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionM = mapOf(
            Pair(dfaStatesM["accStartState"]!!, symo) to dfaStatesM["accStartState"]!!
    )


    private val dfaStatesN = listOf("accStartState", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionN = mapOf(
            Pair(dfaStatesN["accStartState"]!!, symo) to dfaStatesN["accState"]!!
    )

    private val dfaCNG = TestDfa(dfaStatesCNG["startState"]!!, dfaDescriptionCNG)
    private val dfaM = TestDfa(dfaStatesM["accStartState"]!!, dfaDescriptionM)
    private val dfaN = TestDfa(dfaStatesN["accStartState"]!!, dfaDescriptionN)

    private val complicatedNullableGrammar: AutomatonGrammar<String> = AutomatonGrammar(
            start,
            mapOf(
                    start to dfaCNG,
                    symL to getTrivialDfa(),
                    symM to dfaM,
                    symN to dfaN,
            )
    )

    @Test
    fun `test nullable for complicated nullable grammar`() {
        val expectedNullable = setOf(start, symL, symM, symN)
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(complicatedNullableGrammar)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for complicated nullable grammar`() {
        val nullable = setOf(start, symL, symM, symN)

        val expectedFirst = mapOf(
                start to setOf(start, symL, symM, symN, symo),
                symL to setOf(symL),
                symM to setOf(symM, symo),
                symN to setOf(symN, symo),
                symo to setOf(symo)
        )
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(complicatedNullableGrammar, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for complicated nullable grammar`() {
        val nullable = setOf(start, symL, symM, symN)
        val first = mapOf(
                start to setOf(start, symL, symM, symN, symo),
                symL to setOf(symL),
                symM to setOf(symM, symo),
                symN to setOf(symN, symo),
                symo to setOf(symo)
        )

        val expectedFollow = mapOf(
                start to setOf(),
                symL to setOf(start, symL, symM, symN, symo),
                symM to setOf(start, symL, symM, symN, symo),
                symN to setOf(start, symL, symM, symN, symo),
                symo to setOf(start, symL, symM, symN, symo)
        )
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(complicatedNullableGrammar, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }



    // COMPLICATED NULLABLE GRAMMAR (WITH TERMINAL LAST)
    // To test if we can detect p \in FIRST(start) [first after complicated sequence of nullable]
    //
    // Grammar:
    //   Sigma = { start, L, M, N, o, p }
    //   Productions = {
    //      start --> L M M N N N (start) p + N M M L L L (dfaCNG1)
    //      L --> eps (trivial dfa)
    //      M --> o* (dfaM1)
    //      N --> o + eps (dfaN1)
    //   }
    //
    //      Nullable = { start, L, M, N }
    //
    //      First(start) = { start, L, M, N, o, p }
    //      Follow(start) = { }
    //
    //      First(L) = { L }
    //      First(M) = { M, o }
    //      First(N) = { N, o }
    //      First(o) = { o }
    //      First(p) = { p }
    //
    //      Follow(L) = { start, L, M, N, o, p }
    //      Follow(M) = { start, L, M, N, o, p }
    //      Follow(N) = { start, L, M, N, o, p }
    //      Follow(o) = { start, L, M, N, o, p }
    //      Follow(p) = { p }
    //

    private val symp = "p"

    private val dfaStatesCNG1 = listOf(
            "startState",
            "state11", "state12", "state13", "state14", "state15", "state16", "state17",
            "state21", "state22", "state23", "state24", "state25",
            "accState"
    ).map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionCNG1 = mapOf(
            Pair(dfaStatesCNG1["startState"]!!, symL) to dfaStatesCNG1["state11"]!!,
            Pair(dfaStatesCNG1["state11"]!!, symM) to dfaStatesCNG1["state12"]!!,
            Pair(dfaStatesCNG1["state12"]!!, symM) to dfaStatesCNG1["state13"]!!,
            Pair(dfaStatesCNG1["state13"]!!, symN) to dfaStatesCNG1["state14"]!!,
            Pair(dfaStatesCNG1["state14"]!!, symN) to dfaStatesCNG1["state15"]!!,
            Pair(dfaStatesCNG1["state15"]!!, symN) to dfaStatesCNG1["state16"]!!,
            Pair(dfaStatesCNG1["state16"]!!, start) to dfaStatesCNG1["state17"]!!,
            Pair(dfaStatesCNG1["state17"]!!, symp) to dfaStatesCNG1["accState"]!!,
            Pair(dfaStatesCNG1["startState"]!!, symN) to dfaStatesCNG1["state21"]!!,
            Pair(dfaStatesCNG1["state21"]!!, symM) to dfaStatesCNG1["state22"]!!,
            Pair(dfaStatesCNG1["state22"]!!, symM) to dfaStatesCNG1["state23"]!!,
            Pair(dfaStatesCNG1["state23"]!!, symL) to dfaStatesCNG1["state24"]!!,
            Pair(dfaStatesCNG1["state24"]!!, symL) to dfaStatesCNG1["state25"]!!,
            Pair(dfaStatesCNG1["state25"]!!, symL) to dfaStatesCNG1["accState"]!!
    )


    private val dfaStatesM1 = listOf("accStartState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionM1 = mapOf(
            Pair(dfaStatesM1["accStartState"]!!, symo) to dfaStatesM1["accStartState"]!!
    )


    private val dfaStatesN1 = listOf("accStartState", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionN1 = mapOf(
            Pair(dfaStatesN1["accStartState"]!!, symo) to dfaStatesN1["accState"]!!
    )

    private val dfaCNG1 = TestDfa(dfaStatesCNG1["startState"]!!, dfaDescriptionCNG1)
    private val dfaM1 = TestDfa(dfaStatesM1["accStartState"]!!, dfaDescriptionM1)
    private val dfaN1 = TestDfa(dfaStatesN1["accStartState"]!!, dfaDescriptionN1)

    private val complicatedNullableGrammar1: AutomatonGrammar<String> = AutomatonGrammar(
            start,
            mapOf(
                    start to dfaCNG1,
                    symL to getTrivialDfa(),
                    symM to dfaM1,
                    symN to dfaN1,
            )
    )

    @Test
    fun `test nullable for complicated nullable grammar with terminal last`() {
        val expectedNullable = setOf(start, symL, symM, symN)
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(complicatedNullableGrammar1)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for complicated nullable grammar with terminal last`() {
        val nullable = setOf(start, symL, symM, symN)

        val expectedFirst = mapOf(
                start to setOf(start, symL, symM, symN, symo, symp),
                symL to setOf(symL),
                symM to setOf(symM, symo),
                symN to setOf(symN, symo),
                symo to setOf(symo),
                symp to setOf(symp)
        )
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(complicatedNullableGrammar1, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for complicated nullable grammar with terminal last`() {
        val nullable = setOf(start, symL, symM, symN)
        val first = mapOf(
                start to setOf(start, symL, symM, symN, symo, symp),
                symL to setOf(symL),
                symM to setOf(symM, symo),
                symN to setOf(symN, symo),
                symo to setOf(symo),
                symp to setOf(symp)
        )

        val expectedFollow = mapOf(
                start to setOf(),
                symL to setOf(start, symL, symM, symN, symo, symp),
                symM to setOf(start, symL, symM, symN, symo, symp),
                symN to setOf(start, symL, symM, symN, symo, symp),
                symo to setOf(start, symL, symM, symN, symo, symp),
                symp to setOf(symp)
        )
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(complicatedNullableGrammar1, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }



    // COMPLICATED NON NULLABLE GRAMMAR
    // Grammar:
    //   Sigma = { start, Q, R, s }
    //   Productions = {
    //      start --> Q R R (start) + R Q Q (dfaCNNG)
    //      Q --> s (dfaQ)
    //      R --> QQ + s (dfaR)
    //   }
    //
    //      Nullable = { }
    //
    //      First(start) = { start, Q, R, s }
    //      Follow(start) = { }
    //
    //      First(Q) = { Q, s }
    //      First(R) = { Q, R, s }
    //      First(s) = { s }
    //
    //      Follow(Q) = { start, Q, R, s }
    //      Follow(R) = { start, Q, R, s }
    //      Follow(s) = { start, Q, R, s }
    //

    private val symQ = "Q"
    private val symR = "R"
    private val syms = "s"

    private val dfaStatesCNNG = listOf(
            "startState",
            "state11", "state12", "state13",
            "state21", "state22",
            "accState"
    ).map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionCNNG = mapOf(
            Pair(dfaStatesCNNG["startState"]!!, symQ) to dfaStatesCNNG["state11"]!!,
            Pair(dfaStatesCNNG["state11"]!!, symR) to dfaStatesCNNG["state12"]!!,
            Pair(dfaStatesCNNG["state12"]!!, symR) to dfaStatesCNNG["state13"]!!,
            Pair(dfaStatesCNNG["state13"]!!, start) to dfaStatesCNNG["accState"]!!,
            Pair(dfaStatesCNNG["startState"]!!, symR) to dfaStatesCNNG["state21"]!!,
            Pair(dfaStatesCNNG["state21"]!!, symQ) to dfaStatesCNNG["state22"]!!,
            Pair(dfaStatesCNNG["state22"]!!, symQ) to dfaStatesCNNG["accState"]!!
    )


    private val dfaStatesQ = listOf("startState", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionQ = mapOf(
            Pair(dfaStatesQ["startState"]!!, syms) to dfaStatesQ["accState"]!!
    )


    private val dfaStatesR = listOf("startState", "state1", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionR = mapOf(
            Pair(dfaStatesR["startState"]!!, symQ) to dfaStatesR["state1"]!!,
            Pair(dfaStatesR["state1"]!!, symQ) to dfaStatesR["accState"]!!,
            Pair(dfaStatesR["startState"]!!, syms) to dfaStatesR["accState"]!!
    )

    private val dfaCNNG = TestDfa(dfaStatesCNNG["startState"]!!, dfaDescriptionCNNG)
    private val dfaQ = TestDfa(dfaStatesQ["startState"]!!, dfaDescriptionQ)
    private val dfaR = TestDfa(dfaStatesR["startState"]!!, dfaDescriptionR)

    private val complicatedNonNullableGrammar: AutomatonGrammar<String> = AutomatonGrammar(
            start,
            mapOf(
                    start to dfaCNNG,
                    symQ to dfaQ,
                    symR to dfaR
            )
    )

    @Test
    fun `test nullable for complicated non nullable grammar`() {
        val expectedNullable = setOf<GrammarSymbol>()
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(complicatedNonNullableGrammar)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for complicated non nullable grammar`() {
        val nullable = setOf<GrammarSymbol>()

        val expectedFirst = mapOf(
                start to setOf(start, symQ, symR, syms),
                symQ to setOf(symQ, syms),
                symR to setOf(symQ, symR, syms),
                syms to setOf(syms)
        )
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(complicatedNonNullableGrammar, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for complicated non nullable grammar`() {
        val nullable = setOf<GrammarSymbol>()
        val first = mapOf(
                start to setOf(start, symQ, symR, syms),
                symQ to setOf(symQ, syms),
                symR to setOf(symQ, symR, syms),
                syms to setOf(syms)
        )

        val expectedFollow = mapOf(
                start to setOf(start, symQ, symR, syms),
                symQ to setOf(start, symQ, symR, syms),
                symR to setOf(start, symQ, symR, syms),
                syms to setOf(start, symQ, symR, syms)
        )
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(complicatedNonNullableGrammar, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }



    // COMPLICATED NON NULLABLE GRAMMAR (WITH PLUGGED TERMINAL)
    // To test if we can detect t \notin FIRST(start) [t occurs after sth non trivially non nullable]
    //
    // Grammar:
    //   Sigma = { start, Q, R, s, t }
    //   Productions = {
    //      start --> Q R R (start) + R t Q Q (dfaCNNG1)
    //      Q --> s (dfaQ1)
    //      R --> QQ + s (dfaR1)
    //   }
    //
    //      Nullable = { }
    //
    //      First(start) = { start, Q, R, s }
    //      Follow(start) = { }
    //
    //      First(Q) = { Q, s }
    //      First(R) = { Q, R, s }
    //      First(s) = { s }
    //      First(t) = { t }
    //
    //      Follow(Q) = { start, Q, R, s, t }
    //      Follow(R) = { start, Q, R, s, t }
    //      Follow(s) = { start, Q, R, s, t }
    //      Follow(t) = { Q, s }
    //

    private val symt = "t"

    private val dfaStatesCNNG1 = listOf(
            "startState",
            "state11", "state12", "state13",
            "state21", "state22", "state23",
            "accState"
    ).map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionCNNG1 = mapOf(
            Pair(dfaStatesCNNG1["startState"]!!, symQ) to dfaStatesCNNG1["state11"]!!,
            Pair(dfaStatesCNNG1["state11"]!!, symR) to dfaStatesCNNG1["state12"]!!,
            Pair(dfaStatesCNNG1["state12"]!!, symR) to dfaStatesCNNG1["state13"]!!,
            Pair(dfaStatesCNNG1["state13"]!!, start) to dfaStatesCNNG1["accState"]!!,
            Pair(dfaStatesCNNG1["startState"]!!, symR) to dfaStatesCNNG1["state21"]!!,
            Pair(dfaStatesCNNG1["state21"]!!, symt) to dfaStatesCNNG1["state22"]!!,
            Pair(dfaStatesCNNG1["state22"]!!, symQ) to dfaStatesCNNG1["state23"]!!,
            Pair(dfaStatesCNNG1["state23"]!!, symQ) to dfaStatesCNNG1["accState"]!!
    )


    private val dfaStatesQ1 = listOf("startState", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionQ1 = mapOf(
            Pair(dfaStatesQ1["startState"]!!, syms) to dfaStatesQ1["accState"]!!
    )


    private val dfaStatesR1 = listOf("startState", "state1", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionR1 = mapOf(
            Pair(dfaStatesR1["startState"]!!, symQ) to dfaStatesR1["state1"]!!,
            Pair(dfaStatesR1["state1"]!!, symQ) to dfaStatesR1["accState"]!!,
            Pair(dfaStatesR1["startState"]!!, syms) to dfaStatesR1["accState"]!!
    )

    private val dfaCNNG1 = TestDfa(dfaStatesCNNG1["startState"]!!, dfaDescriptionCNNG1)
    private val dfaQ1 = TestDfa(dfaStatesQ1["startState"]!!, dfaDescriptionQ1)
    private val dfaR1 = TestDfa(dfaStatesR1["startState"]!!, dfaDescriptionR1)

    private val complicatedNonNullableGrammar1: AutomatonGrammar<String> = AutomatonGrammar(
            start,
            mapOf(
                    start to dfaCNNG1,
                    symQ to dfaQ1,
                    symR to dfaR1
            )
    )

    @Test
    fun `test nullable for complicated non nullable grammar with plugged terminal`() {
        val expectedNullable = setOf<GrammarSymbol>()
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(complicatedNonNullableGrammar1)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for complicated non nullable grammar with plugged terminal`() {
        val nullable = setOf<GrammarSymbol>()

        val expectedFirst = mapOf(
                start to setOf(start, symQ, symR, syms),
                symQ to setOf(symQ, syms),
                symR to setOf(symQ, symR, syms),
                syms to setOf(syms),
                symt to setOf(symt)
        )
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(complicatedNonNullableGrammar1, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for complicated non nullable grammar with plugged terminal`() {
        val nullable = setOf<GrammarSymbol>()
        val first = mapOf(
                start to setOf(start, symQ, symR, syms),
                symQ to setOf(symQ, syms),
                symR to setOf(symQ, symR, syms),
                syms to setOf(syms),
                symt to setOf(symt)
        )

        val expectedFollow = mapOf(
                start to setOf(start, symQ, symR, syms, symt),
                symQ to setOf(start, symQ, symR, syms, symt),
                symR to setOf(start, symQ, symR, syms, symt),
                syms to setOf(start, symQ, symR, syms, symt),
                symt to setOf(start, symQ, syms)
        )
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(complicatedNonNullableGrammar1, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }



    // TEST FOLLOW GRAMMAR
    //
    // Grammar:
    //   Sigma = { start, u, V, w, x, y, z }
    //   Productions = {
    //      start --> u (V + w)* x + x x (dfaFol)
    //      V --> y z + z* (dfaV)
    //   }
    //
    //      Nullable = { V }
    //
    //      First(start) = { start, u, x }
    //      Follow(start) = { }
    //
    //      First(u) = { u }
    //      First(V) = { V, y, z }
    //      First(w) = { w }
    //      First(x) = { x }
    //      First(y) = { y }
    //      First(z) = { z }
    //
    //      Follow(u) = { V, w, x, y, z }
    //      Follow(V) = { V, w, x, y, z }
    //      Follow(w) = { V, w, z, y, z }
    //      Follow(x) = { x }
    //      Follow(y) = { z }
    //      Follow(z) = { V, w, z, y, z }
    //

    private val symu = "u"
    private val symV = "V"
    private val symw = "w"
    private val symx = "x"
    private val symy = "y"
    private val symz = "z"

    private val dfaStatesFol = listOf("startState", "state1", "state2", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionFol = mapOf(
            Pair(dfaStatesFol["startState"]!!, symu) to dfaStatesFol["state1"]!!,
            Pair(dfaStatesFol["state1"]!!, symV) to dfaStatesFol["state1"]!!,
            Pair(dfaStatesFol["state1"]!!, symw) to dfaStatesFol["state1"]!!,
            Pair(dfaStatesFol["state1"]!!, symx) to dfaStatesFol["accState"]!!,
            Pair(dfaStatesFol["startState"]!!, symx) to dfaStatesFol["state2"]!!,
            Pair(dfaStatesFol["state2"]!!, symx) to dfaStatesFol["accState"]!!
    )


    private val dfaStatesV = listOf("startState", "state1", "state2", "accState").map { it to TestDfaState(it) }.toMap()
    private val dfaDescriptionV = mapOf(
            Pair(dfaStatesV["startState"]!!, symy) to dfaStatesV["state1"]!!,
            Pair(dfaStatesV["state1"]!!, symz) to dfaStatesV["accState"]!!,
            Pair(dfaStatesV["startState"]!!, EPSILON) to dfaStatesV["state2"]!!,
            Pair(dfaStatesV["state2"]!!, symz) to dfaStatesV["state2"]!!,
            Pair(dfaStatesV["state2"]!!, EPSILON) to dfaStatesV["accState"]!!
    )

    private val dfaFol = TestDfa(dfaStatesFol["startState"]!!, dfaDescriptionFol)
    private val dfaV = TestDfa(dfaStatesV["startState"]!!, dfaDescriptionV)

    private val followGrammar: AutomatonGrammar<String> = AutomatonGrammar(
            start,
            mapOf(
                    start to dfaFol,
                    symV to dfaV
            )
    )

    @Test
    fun `test nullable for follow grammar`() {
        val expectedNullable = setOf(symV)
        val actualNullable = GrammarAnalysis<GrammarSymbol>().computeNullable(followGrammar)

        assertEquals(expectedNullable, actualNullable)
    }

    @Test
    fun `test first for follow grammar`() {
        val nullable = setOf(symV)

        val expectedFirst = mapOf(
                start to setOf(start, symu, symx),
                symu to setOf(symu),
                symV to setOf(symV, symy, symz),
                symw to setOf(symw),
                symx to setOf(symx),
                symy to setOf(symy),
                symz to setOf(symz)
        )
        val actualFirst = GrammarAnalysis<GrammarSymbol>().computeFirst(followGrammar, nullable)

        assertEquals(expectedFirst, actualFirst)
    }

    @Test
    fun `test follow for follow grammar`() {
        val nullable = setOf(symV)
        val first = mapOf(
                start to setOf(start, symu, symx),
                symu to setOf(symu),
                symV to setOf(symV, symy, symz),
                symw to setOf(symw),
                symx to setOf(symx),
                symy to setOf(symy),
                symz to setOf(symz)
        )

        val expectedFollow = mapOf(
                start to setOf(),
                symu to setOf(symV, symw, symx, symy, symz),
                symV to setOf(symV, symw, symx, symy, symz),
                symw to setOf(symV, symw, symx, symy, symz),
                symx to setOf(symx),
                symy to setOf(symz),
                symz to setOf(symV, symw, symx, symy, symz)
        )
        val actualFollow = GrammarAnalysis<GrammarSymbol>().computeFollow(followGrammar, nullable, first)

        assertEquals(expectedFollow, actualFollow)
    }

}
