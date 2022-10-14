package compiler.lexer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContains
import compiler.lexer.input.Input
import compiler.lexer.dfa.Dfa
import compiler.lexer.dfa.DfaWalk

class LexerTest {
    private class TestInput(val string: String) : Input {
        var index = 0
        val flushed = ArrayList<Int>()

        override fun getLocation() = Location(1, index + 1)

        override fun hasNext() = index < string.length

        override fun next() = string[index++]

        override fun rewind(count: Int) {
            index -= count
        }

        override fun flush() {
            flushed.add(index)
        }
    }

    private class TestDfa(val pattern: String) : Dfa {
        override fun newWalk() = object : DfaWalk {
            var index = 0

            override fun step(a: Char) {
                if (index < pattern.length && a == pattern[index])
                    index++
                else
                    index = pattern.length + 1
            }

            override fun isAccepted() = index == pattern.length

            override fun isDead() = index == pattern.length + 1
        }
    }

    @Test fun `empty input results in no tokens`() {
        val input = TestInput("")
        val lexer = Lexer<Unit>(emptyList())

        val result = lexer.process(input)

        assertContentEquals(emptySequence(), result)
    }

    @Test fun `a single token is matched`() {
        val input = TestInput("ab")
        val dfa = TestDfa("ab")
        val lexer = Lexer<Unit>(listOf(Pair(dfa, Unit)))

        val result = lexer.process(input)

        val token = Token(Unit, "ab", Location(1, 1), Location(1, 2))
        assertContentEquals(sequenceOf(token), result)
    }

    @Test fun `multiple tokens are matched`() {
        val input = TestInput("aaabab")
        val dfa1 = TestDfa("aa")
        val dfa2 = TestDfa("ab")
        val lexer = Lexer<Boolean>(listOf(Pair(dfa1, true), Pair(dfa2, false)))

        val result = lexer.process(input)

        val token1 = Token(true, "aa", Location(1, 1), Location(1, 2))
        val token2 = Token(false, "ab", Location(1, 3), Location(1, 4))
        val token3 = Token(false, "ab", Location(1, 5), Location(1, 6))
        assertContentEquals(sequenceOf(token1, token2, token3), result)
    }

    @Test fun `the token matching is greedy`() {
        val input = TestInput("aaa")
        val dfa1 = TestDfa("a")
        val dfa2 = TestDfa("aa")
        val dfa3 = TestDfa("aab")
        val lexer = Lexer<Int>(listOf(Pair(dfa1, 1), Pair(dfa2, 2), Pair(dfa3, 3)))

        val result = lexer.process(input)

        val token1 = Token(2, "aa", Location(1, 1), Location(1, 2))
        val token2 = Token(1, "a", Location(1, 3), Location(1, 3))
        assertContentEquals(sequenceOf(token1, token2), result)
    }

    @Test fun `a token category earlier in the list has higher priority`() {
        val input1 = TestInput("ab")
        val input2 = TestInput("ab")
        val dfa = TestDfa("ab")
        val lexer1 = Lexer<Boolean>(listOf(Pair(dfa, true), Pair(dfa, false)))
        val lexer2 = Lexer<Boolean>(listOf(Pair(dfa, false), Pair(dfa, true)))

        val result1 = lexer1.process(input1)
        val result2 = lexer2.process(input2)

        val token1 = Token(true, "ab", Location(1, 1), Location(1, 2))
        val token2 = Token(false, "ab", Location(1, 1), Location(1, 2))
        assertContentEquals(sequenceOf(token1), result1)
        assertContentEquals(sequenceOf(token2), result2)
    }

    @Test fun `an input matching to no tokens results in an error`() {
        val input = TestInput("ab")
        val dfa = TestDfa("a")
        val lexer = Lexer<Unit>(listOf(Pair(dfa, Unit)))

        assertFailsWith(Lexer.Fail::class) {
            lexer.process(input).count()
        }
    }

    @Test fun `the lexer flushes the input after matching a token`() {
        val input = TestInput("ababab")
        val dfa = TestDfa("ab")
        val lexer = Lexer<Unit>(listOf(Pair(dfa, Unit)))

        lexer.process(input).count()

        assertContains(input.flushed, 2)
        assertContains(input.flushed, 4)
    }
}
