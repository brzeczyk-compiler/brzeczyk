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

    @Test fun `test possible steps, results and predecessors on an example regex`() {
        val regex: Regex<Char> = RegexFactory.createUnion(
            RegexFactory.createConcat(
                RegexFactory.createConcat(
                    RegexFactory.createAtomic(setOf('a')),
                    RegexFactory.createAtomic(setOf('b'))
                ),
                RegexFactory.createAtomic(setOf('c'))
            ),
            RegexFactory.createConcat(
                RegexFactory.createConcat(
                    RegexFactory.createAtomic(setOf('c')),
                    RegexFactory.createStar(
                        RegexFactory.createAtomic(setOf('a', 'b'))
                    )
                ),
                RegexFactory.createAtomic(setOf('c'))
            )
        )

        val regexDfa = RegexDfa(regex)

        val stateStart = regexDfa.startState
        val stateBC = RegexDfaState(
            RegexFactory.createConcat(
                RegexFactory.createAtomic(setOf('b')),
                RegexFactory.createAtomic(setOf('c'))
            )
        )
        val stateC = RegexDfaState(RegexFactory.createAtomic(setOf('c')))
        val stateEPS = RegexDfaState(RegexFactory.createEpsilon<Char>())
        val stateABStarC = RegexDfaState(
            RegexFactory.createConcat(
                RegexFactory.createStar(
                    RegexFactory.createAtomic(setOf('a', 'b'))
                ),
                RegexFactory.createAtomic(setOf('c')),
            )
        )

        assertEquals(
            stateStart.possibleSteps,
            mapOf(
                'a' to stateBC,
                'c' to stateABStarC
            )
        )
        assertEquals(stateBC.possibleSteps, mapOf('b' to stateC))
        assertEquals(stateC.possibleSteps, mapOf('c' to stateEPS))
        assertEquals(stateEPS.possibleSteps, emptyMap())
        assertEquals(
            stateABStarC.possibleSteps,
            mapOf(
                'a' to stateABStarC,
                'b' to stateABStarC,
                'c' to stateEPS
            )
        )

        assertEquals(regexDfa.getPredecessors(stateStart), emptyMap())
        assertEquals(
            regexDfa.getPredecessors(stateBC),
            mapOf('a' to setOf(stateStart))
        )
        assertEquals(
            regexDfa.getPredecessors(stateC),
            mapOf('b' to setOf(stateBC))
        )
        assertEquals(
            regexDfa.getPredecessors(stateEPS),
            mapOf('c' to setOf(stateABStarC, stateC))
        )
        assertEquals(
            regexDfa.getPredecessors(stateABStarC),
            mapOf(
                'a' to setOf(stateABStarC),
                'b' to setOf(stateABStarC),
                'c' to setOf(stateStart)
            )
        )

        assertEquals(regexDfa.getAcceptingStates(), setOf(stateEPS))
        assertNull(stateBC.result)
        assertNull(stateC.result)
        assertEquals(stateEPS.result, Unit)
        assertNull(stateABStarC.result)
    }

    @Test fun `test possible steps, results and predecessors on example regex 2`() {
        val regex: Regex<Char> = RegexParser.parseStringToRegex("([ab][cd])*a*")

        val regexDfa = RegexDfa(regex)
        val stateStart = regexDfa.startState
        val state1 = RegexDfaState(RegexParser.parseStringToRegex("([cd]([ab][cd])*a*)|a*"))
        val state2 = RegexDfaState(RegexParser.parseStringToRegex("([cd]([ab][cd])*a*)"))
        val state3 = RegexDfaState(RegexParser.parseStringToRegex("a*"))

        assertEquals(
            stateStart.possibleSteps,
            mapOf(
                'a' to state1,
                'b' to state2
            )
        )
        assertEquals(
            state1.possibleSteps,
            mapOf(
                'a' to state3,
                'c' to stateStart,
                'd' to stateStart
            )
        )
        assertEquals(
            state2.possibleSteps,
            mapOf(
                'c' to stateStart,
                'd' to stateStart
            )
        )
        assertEquals(state3.possibleSteps, mapOf('a' to state3))

        assertEquals(
            regexDfa.getPredecessors(stateStart),
            mapOf(
                'c' to setOf(state1, state2),
                'd' to setOf(state1, state2)
            )
        )
        assertEquals(
            regexDfa.getPredecessors(state1),
            mapOf('a' to setOf(stateStart))
        )
        assertEquals(
            regexDfa.getPredecessors(state2),
            mapOf('b' to setOf(stateStart))
        )
        assertEquals(
            regexDfa.getPredecessors(state3),
            mapOf('a' to setOf(state1, state3))
        )

        assertEquals(regexDfa.getAcceptingStates(), setOf(stateStart, state1, state3))
        assertEquals(stateStart.result, Unit)
        assertEquals(state1.result, Unit)
        assertNull(state2.result)
        assertEquals(state3.result, Unit)
    }
}
