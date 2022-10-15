package compiler.lexer.dfa
import compiler.lexer.regex.Regex
import compiler.lexer.regex.RegexFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RegexDfaTest {
    private fun abRegexDfa(): RegexDfa {
        return RegexDfa(
            RegexFactory.createConcat(
                RegexFactory.createAtomic(setOf('a')),
                RegexFactory.createAtomic(setOf('b'))
            )
        )
    }

    @Test fun walkOnEmptyRegexIsDead() {
        val regexDfa = RegexDfa(RegexFactory.createEmpty())
        val walk = regexDfa.newWalk()
        assert(walk.isDead())
        assert(!walk.isAccepted())
    }

    @Test fun walkOnEpsilonRegexIsAccepted() {
        val regexDfa = RegexDfa(RegexFactory.createEpsilon())
        val walk = regexDfa.newWalk()
        assert(!walk.isDead())
        assert(walk.isAccepted())
    }

    @Test fun walksAreIndependent() {
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
            for (char in word) {
                walk.step(char)
            }
            if (walk.isAccepted())
                assertEquals("ab", word)
            else
                assertNotEquals("ab", word)
        }
    }

    @Test fun `walk on ab RegexDfa correctly computes dead states`() {
        val words = listOf("aa", "ab", "ac", "ba", "bb", "aba", "abab", "a", "b", "")
        val notDeadWalks = listOf("ab", "a", "")
        val regexDfa = abRegexDfa()

        for (word in words) {
            val walk = regexDfa.newWalk()
            for (char in word) {
                walk.step(char)
            }
            val shouldStateBeAlive = notDeadWalks.contains(word)
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
            for (char in word) {
                walk.step(char)
            }

            val shouldStateBeAccepting = word.all { char: Char -> setOf('a', 'b').contains(char) } && word.last() == 'b'
            assertEquals(shouldStateBeAccepting, walk.isAccepted(), "Is walk over $word accepted?")
        }
    }
}
