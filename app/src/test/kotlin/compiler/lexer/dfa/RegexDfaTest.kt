package compiler.lexer.dfa
import compiler.common.dfa.RegexDfa
import compiler.common.dfa.RegexDfaState
import compiler.common.dfa.isAccepting
import compiler.common.regex.Regex
import compiler.common.regex.RegexFactory
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

    @Test fun `test possible states and results on example regex`() {
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
            regexDfa.startState.possibleSteps,
            mapOf(
                'a' to stateBC,
                'c' to stateABStarC
            )
        )
        assertEquals(stateBC.possibleSteps, mapOf('b' to stateC,))
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
        assertNull(stateBC.result)
        assertNull(stateC.result)
        assertEquals(stateEPS.result, Unit)
        assertNull(stateABStarC.result)
    }
}
