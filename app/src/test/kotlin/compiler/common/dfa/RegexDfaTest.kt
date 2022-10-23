package compiler.common.dfa
import compiler.common.regex.Regex
import compiler.common.regex.RegexFactory
import compiler.lexer.lexer_grammar.RegexParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RegexDfaTest {
    private fun abRegexDfa(): RegexDfa<Char> {
        return RegexDfa(
            RegexFactory.createConcat(
                RegexFactory.createAtomic(setOf('a')),
                RegexFactory.createAtomic(setOf('b'))
            )
        )
    }

    @Test fun `walk on empty regex is dead`() {
        val regexDfa = RegexDfa<Char>(RegexFactory.createEmpty())
        val walk = regexDfa.newWalk()
        assert(walk.isDead())
        assert(!walk.isAccepting())
    }

    @Test fun `walk on epsilon regex is accepted`() {
        val regexDfa = RegexDfa<Char>(RegexFactory.createEpsilon())
        val walk = regexDfa.newWalk()
        assert(!walk.isDead())
        assert(walk.isAccepting())
    }

    @Test fun `walks are independent`() {
        val regexDfa = abRegexDfa()
        val walk1 = regexDfa.newWalk()
        val walk2 = regexDfa.newWalk()
        walk2.step('b')

        assert(!walk1.isDead())
        assert(walk2.isDead())
    }

    @Test fun `walk on ab RegexDfa accepts only ab`() {
        val words = listOf("aa", "ab", "ac", "ba", "bb", "aba", "abab", "a", "b", "")
        val regexDfa = abRegexDfa()

        for (word in words) {
            val walk = regexDfa.newWalk()
            word.forEach { walk.step(it) }

            if (walk.isAccepting())
                assertEquals("ab", word)
            else
                assertNotEquals("ab", word)
        }
    }

    @Test fun `walk on ab RegexDfa correctly computes dead states`() {
        val words = listOf("aa", "ab", "ac", "ba", "bb", "aba", "abab", "a", "b", "")
        val wordsToNotBeDead = listOf("ab", "a", "")
        val regexDfa = abRegexDfa()

        for (word in words) {
            val walk = regexDfa.newWalk()
            word.forEach { walk.step(it) }

            val shouldStateBeAlive = wordsToNotBeDead.contains(word)
            assertEquals(shouldStateBeAlive, !walk.isDead(), "After walk over $word is state alive?")
        }
    }

    @Test fun `walk on (a+b)'star'b RegexDfa accepts all words over alphabet ab ending with b`() {
        val words = listOf("aa", "ab", "ac", "ba", "bb", "aba", "abab", "a", "b", "", "abcb", "aaaab")
        val regex = Regex.Concat(
            Regex.Star(
                Regex.Union(
                    Regex.Atomic(setOf('a')),
                    Regex.Atomic(setOf('b'))
                )
            ),
            Regex.Atomic(setOf('b'))
        )
        val regexDfa = RegexDfa(regex)

        for (word in words) {
            val walk = regexDfa.newWalk()
            word.forEach { walk.step(it) }

            val shouldStateBeAccepting = word.lastOrNull() == 'b' && word.all { setOf('a', 'b').contains(it) }
            assertEquals(shouldStateBeAccepting, walk.isAccepting(), "Is walk over $word accepted?")
        }
    }

    // Example RegexDfa for ((abc)|(c(a|b)*c)
    private val dfa1 = RegexDfa(RegexParser.parseStringToRegex("(abc)|(c(a|b)*c)"))
    private val dfa1StateStart = dfa1.startState
    private val dfa1StateBC = RegexDfaState(RegexParser.parseStringToRegex("bc"))
    private val dfa1StateC = RegexDfaState(RegexParser.parseStringToRegex("c"))
    private val dfa1StateEPS = RegexDfaState(RegexFactory.createEpsilon<Char>())
    private val dfa1StateABStarC = RegexDfaState(RegexParser.parseStringToRegex("(a|b)*c"))

    // Example RegexDfa for ([ab][cd])*a*
    private val dfa2 = RegexDfa(RegexParser.parseStringToRegex("([ab][cd])*a*"))
    private val dfa2StateStart = dfa2.startState
    private val dfa2State1 = RegexDfaState(RegexParser.parseStringToRegex("([cd]([ab][cd])*a*)|a*"))
    private val dfa2State2 = RegexDfaState(RegexParser.parseStringToRegex("([cd]([ab][cd])*a*)"))
    private val dfa2State3 = RegexDfaState(RegexParser.parseStringToRegex("a*"))

    @Test fun `test possible steps for states of example automata`() {
        // dfa1
        assertEquals(
            dfa1StateStart.possibleSteps,
            mapOf(
                'a' to dfa1StateBC,
                'c' to dfa1StateABStarC
            )
        )
        assertEquals(dfa1StateBC.possibleSteps, mapOf('b' to dfa1StateC))
        assertEquals(dfa1StateC.possibleSteps, mapOf('c' to dfa1StateEPS))
        assertEquals(dfa1StateEPS.possibleSteps, emptyMap())
        assertEquals(
            dfa1StateABStarC.possibleSteps,
            mapOf(
                'a' to dfa1StateABStarC,
                'b' to dfa1StateABStarC,
                'c' to dfa1StateEPS
            )
        )

        // dfa2
        assertEquals(
            dfa2StateStart.possibleSteps,
            mapOf(
                'a' to dfa2State1,
                'b' to dfa2State2
            )
        )
        assertEquals(
            dfa2State1.possibleSteps,
            mapOf(
                'a' to dfa2State3,
                'c' to dfa2StateStart,
                'd' to dfa2StateStart
            )
        )
        assertEquals(
            dfa2State2.possibleSteps,
            mapOf(
                'c' to dfa2StateStart,
                'd' to dfa2StateStart
            )
        )
        assertEquals(dfa2State3.possibleSteps, mapOf('a' to dfa2State3))
    }

    @Test fun `test predecessors for states of example automata`() {
        // dfa1
        assertEquals(dfa1.getPredecessors(dfa1StateStart), emptyMap())
        assertEquals(
            dfa1.getPredecessors(dfa1StateBC),
            mapOf('a' to setOf(dfa1StateStart))
        )
        assertEquals(
            dfa1.getPredecessors(dfa1StateC),
            mapOf('b' to setOf(dfa1StateBC))
        )
        assertEquals(
            dfa1.getPredecessors(dfa1StateEPS),
            mapOf('c' to setOf(dfa1StateABStarC, dfa1StateC))
        )
        assertEquals(
            dfa1.getPredecessors(dfa1StateABStarC),
            mapOf(
                'a' to setOf(dfa1StateABStarC),
                'b' to setOf(dfa1StateABStarC),
                'c' to setOf(dfa1StateStart)
            )
        )

        // dfa2
        assertEquals(
            dfa2.getPredecessors(dfa2StateStart),
            mapOf(
                'c' to setOf(dfa2State1, dfa2State2),
                'd' to setOf(dfa2State1, dfa2State2)
            )
        )
        assertEquals(
            dfa2.getPredecessors(dfa2State1),
            mapOf('a' to setOf(dfa2StateStart))
        )
        assertEquals(
            dfa2.getPredecessors(dfa2State2),
            mapOf('b' to setOf(dfa2StateStart))
        )
        assertEquals(
            dfa2.getPredecessors(dfa2State3),
            mapOf('a' to setOf(dfa2State1, dfa2State3))
        )
    }

    @Test fun `test results for states of example automata`() {
        // dfa1
        assertEquals(dfa1.getAcceptingStates(), setOf(dfa1StateEPS))
        assertNull(dfa1StateBC.result)
        assertNull(dfa1StateC.result)
        assertEquals(dfa1StateEPS.result, Unit)
        assertNull(dfa1StateABStarC.result)

        // dfa2
        assertEquals(dfa2.getAcceptingStates(), setOf(dfa2StateStart, dfa2State1, dfa2State3))
        assertEquals(dfa2StateStart.result, Unit)
        assertEquals(dfa2State1.result, Unit)
        assertNull(dfa2State2.result)
        assertEquals(dfa2State3.result, Unit)
    }
}
