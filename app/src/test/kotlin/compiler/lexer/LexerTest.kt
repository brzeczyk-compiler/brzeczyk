package compiler.lexer

import compiler.dfa.AbstractDfa
import compiler.dfa.DfaWalk
import compiler.diagnostics.Diagnostic
import compiler.diagnostics.Diagnostics
import compiler.input.Input
import compiler.input.Location
import compiler.input.LocationRange
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class LexerTest {
    private class TestInput(val string: String) : Input {
        var index = 0
        val flushed = mutableListOf(0)

        override fun getLocation() = Location(1, index + 1)

        override fun hasNext() = index < string.length

        override fun next() = if (hasNext()) string[index++] else fail()

        override fun rewind(count: Int) {
            index -= count
            if (flushed.any { index < it })
                fail()
        }

        override fun flush() {
            flushed.add(index)
        }
    }

    private class TestDfa(val pattern: String) : AbstractDfa<Char, Unit> {
        override fun newWalk() = object : DfaWalk<Char, Unit> {
            var state = 0

            override fun step(a: Char) {
                if (state < pattern.length && a == pattern[state])
                    state++
                else
                    state = pattern.length + 1
            }

            override fun getAcceptingStateTypeOrNull(): Unit? {
                if (state == pattern.length)
                    return Unit
                return null
            }

            override fun isDead() = state == pattern.length + 1
        }
    }

    class TestDiagnostics : Diagnostics {
        val reports: MutableList<Diagnostic> = mutableListOf()

        override fun report(diagnostic: Diagnostic) {
            reports.add(diagnostic)
        }

        override fun hasAnyErrors() = reports.any { it.isError }
    }

    @Test fun `empty input results in no tokens`() {
        val input = TestInput("")
        val lexer = Lexer<Unit>(emptyList(), TestDiagnostics())

        val result = lexer.process(input).toList()

        assertEquals(emptyList(), result)
    }

    @Test fun `a single token is matched`() {
        val input = TestInput("ab")
        val dfa = TestDfa("ab")
        val lexer = Lexer(listOf(Pair(dfa, Unit)), TestDiagnostics())

        val result = lexer.process(input).toList()

        val token = Token(Unit, "ab", LocationRange(Location(1, 1), Location(1, 2)))
        assertEquals(listOf(token), result)
    }

    @Test fun `multiple tokens are matched`() {
        val input = TestInput("aaabab")
        val dfa1 = TestDfa("aa")
        val dfa2 = TestDfa("ab")
        val lexer = Lexer(listOf(Pair(dfa1, 1), Pair(dfa2, 2)), TestDiagnostics())

        val result = lexer.process(input).toList()

        val token1 = Token(1, "aa", LocationRange(Location(1, 1), Location(1, 2)))
        val token2 = Token(2, "ab", LocationRange(Location(1, 3), Location(1, 4)))
        val token3 = Token(2, "ab", LocationRange(Location(1, 5), Location(1, 6)))
        assertEquals(listOf(token1, token2, token3), result)
    }

    @Test fun `the token matching is greedy`() {
        val input = TestInput("aaa")
        val dfa1 = TestDfa("a")
        val dfa2 = TestDfa("aa")
        val lexer = Lexer(listOf(Pair(dfa1, 1), Pair(dfa2, 2)), TestDiagnostics())

        val result = lexer.process(input).toList()

        val token1 = Token(2, "aa", LocationRange(Location(1, 1), Location(1, 2)))
        val token2 = Token(1, "a", LocationRange(Location(1, 3), Location(1, 3)))
        assertEquals(listOf(token1, token2), result)
    }

    @Test fun `the lexer can correctly rewind input`() {
        val input = TestInput("aaa")
        val dfa1 = TestDfa("a")
        val dfa2 = TestDfa("aab")
        val lexer = Lexer(listOf(Pair(dfa1, 1), Pair(dfa2, 2)), TestDiagnostics())

        val result = lexer.process(input).toList()

        val token1 = Token(1, "a", LocationRange(Location(1, 1), Location(1, 1)))
        val token2 = Token(1, "a", LocationRange(Location(1, 2), Location(1, 2)))
        val token3 = Token(1, "a", LocationRange(Location(1, 3), Location(1, 3)))
        assertEquals(listOf(token1, token2, token3), result)
    }

    @Test fun `a token category earlier in the list has higher priority`() {
        val input1 = TestInput("ab")
        val input2 = TestInput("ab")
        val dfa = TestDfa("ab")
        val lexer1 = Lexer(listOf(Pair(dfa, 1), Pair(dfa, 2)), TestDiagnostics())
        val lexer2 = Lexer(listOf(Pair(dfa, 2), Pair(dfa, 1)), TestDiagnostics())

        val result1 = lexer1.process(input1).toList()
        val result2 = lexer2.process(input2).toList()

        val token1 = Token(1, "ab", LocationRange(Location(1, 1), Location(1, 2)))
        val token2 = Token(2, "ab", LocationRange(Location(1, 1), Location(1, 2)))
        assertEquals(listOf(token1), result1)
        assertEquals(listOf(token2), result2)
    }

    @Test fun `the lexer flushes the input after matching a token`() {
        val input = TestInput("ababab")
        val dfa = TestDfa("ab")
        val lexer = Lexer(listOf(Pair(dfa, Unit)), TestDiagnostics())

        lexer.process(input).count()

        assertContains(input.flushed, 2)
        assertContains(input.flushed, 4)
    }

    @Test fun `an input matching to no tokens results only in an error`() {
        val input = TestInput("a")
        val dfa = TestDfa("b")
        val diagnostics = TestDiagnostics()
        val lexer = Lexer(listOf(Pair(dfa, Unit)), diagnostics)

        val result = lexer.process(input)

        assertEquals(0, result.count())
        assertEquals(1, diagnostics.reports.size)
        assertTrue(diagnostics.reports[0].isError)
        assertIs<Diagnostic.LexerError>(diagnostics.reports[0])
    }

    @Test fun `the lexer ignores faulty input and correctly reads the rest`() {
        val input = TestInput("XtokenYothertokenZ")
        val dfa1 = TestDfa("token")
        val dfa2 = TestDfa("othertoken")
        val lexer = Lexer(listOf(Pair(dfa1, 1), Pair(dfa2, 2)), TestDiagnostics())

        val result = lexer.process(input).toList()

        val token1 = Token(1, "token", LocationRange(Location(1, 2), Location(1, 6)))
        val token2 = Token(2, "othertoken", LocationRange(Location(1, 8), Location(1, 17)))
        assertEquals(listOf(token1, token2), result)
    }

    @Test fun `multiple errors are reported`() {
        val input = TestInput("XtokenYothertokenZ")
        val dfa1 = TestDfa("token")
        val dfa2 = TestDfa("othertoken")
        val diagnostics = TestDiagnostics()
        val lexer = Lexer(listOf(Pair(dfa1, 1), Pair(dfa2, 2)), diagnostics)

        lexer.process(input).count()

        assertEquals(3, diagnostics.reports.size)

        diagnostics.reports.forEach {
            assertIs<Diagnostic.LexerError>(it)
        }
    }

    @Test fun `errors are reported with correct locations`() {
        val input = TestInput("XtokenYYothertokenZZZ")
        val dfa1 = TestDfa("token")
        val dfa2 = TestDfa("othertoken")
        val diagnostics = TestDiagnostics()
        val lexer = Lexer(listOf(Pair(dfa1, 1), Pair(dfa2, 2)), diagnostics)

        lexer.process(input).count()

        assertEquals(3, diagnostics.reports.size)

        val errorX = diagnostics.reports[0]
        val errorY = diagnostics.reports[1]
        val errorZ = diagnostics.reports[2]

        assertIs<Diagnostic.LexerError>(errorX)
        assertIs<Diagnostic.LexerError>(errorY)
        assertIs<Diagnostic.LexerError>(errorZ)

        assertEquals(Location(1, 1), errorX.start)
        assertEquals(Location(1, 2), errorX.end)
        assertEquals(Location(1, "XtokenY".length), errorY.start)
        assertEquals(Location(1, "XtokenYY".length + 1), errorY.end)
        assertEquals(Location(1, "XtokenYYothertokenZ".length), errorZ.start)
        assertEquals(null, errorZ.end)
    }

    @Test fun `errors are reported with correct error segments`() {
        val input = TestInput("XtokenYYothertokenZZZ")
        val dfa1 = TestDfa("token")
        val dfa2 = TestDfa("othertoken")
        val diagnostics = TestDiagnostics()
        val lexer = Lexer(listOf(Pair(dfa1, 1), Pair(dfa2, 2)), diagnostics)

        lexer.process(input).count()

        assertEquals(3, diagnostics.reports.size)

        val errorX = diagnostics.reports[0]
        val errorY = diagnostics.reports[1]
        val errorZ = diagnostics.reports[2]

        assertIs<Diagnostic.LexerError>(errorX)
        assertIs<Diagnostic.LexerError>(errorY)
        assertIs<Diagnostic.LexerError>(errorZ)

        assertEquals("X", errorX.errorSegment)
        assertEquals("YY", errorY.errorSegment)
        assertEquals("ZZZ", errorZ.errorSegment)
    }

    @Test fun `errors are reported with correct context`() {
        val input = TestInput("111xxx222333yyy444zzz")
        val dfa1 = TestDfa("111")
        val dfa2 = TestDfa("222")
        val dfa3 = TestDfa("333")
        val dfa4 = TestDfa("444")
        val diagnostics = TestDiagnostics()
        val lexer = Lexer(listOf(Pair(dfa1, 1), Pair(dfa2, 2), Pair(dfa3, 3), Pair(dfa4, 4)), diagnostics, 3)

        lexer.process(input).count()

        assertEquals(3, diagnostics.reports.size)

        val errorX = diagnostics.reports[0]
        val errorY = diagnostics.reports[1]
        val errorZ = diagnostics.reports[2]

        assertIs<Diagnostic.LexerError>(errorX)
        assertIs<Diagnostic.LexerError>(errorY)
        assertIs<Diagnostic.LexerError>(errorZ)

        assertEquals(listOf("111"), errorX.context)
        assertEquals(listOf("xxx", "222", "333"), errorY.context)
        assertEquals(listOf("333", "yyy", "444"), errorZ.context)
    }
}
