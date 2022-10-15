package compiler.lexer.lexer_grammar

import kotlin.test.Test
import kotlin.test.assertEquals

internal class RegexParserTest {

    class TestRegexParser : UniversalRegexParser<String>() {
        override fun starBehaviour(a: String): String {
            return "(*$a*)"
        }

        override fun concatBehaviour(a: String, b: String): String {
            return "(?$a,$b?)"
        }

        override fun unionBehaviour(a: String, b: String): String {
            return "(|$a,$b|)"
        }

        override fun getEmpty(): String {
            return "EMP"
        }

        override fun getAtomic(s: Set<Char>): String {
            return if (s.size == 1) s.toList()[0].toString()
            else "(&" + s.toList().sorted().joinToString("") + "&)"
        }
    }

    companion object {
        val PARSER = TestRegexParser()
    }

    @Test
    fun basicOperations() {
        val star = PARSER.parseStringToRegex("a*")
        val concat = PARSER.parseStringToRegex("ab")
        val union = PARSER.parseStringToRegex("a|b")

        assertEquals("(*a*)", star)
        assertEquals("(?b,a?)", concat)
        assertEquals("(|b,a|)", union)
    }

    @Test
    fun concatTest() {
        val concat = PARSER.parseStringToRegex("ab*c(x|d)[dd](d)*(xd)")

        assertEquals("(?(?d,x?),(?(*d*),(?(|(|EMP,d|),d|),(?(|d,x|),(?c,(?(*b*),a?)?)?)?)?)?)", concat)
    }

    @Test
    fun bracketsTest() {
        val bracketsA = PARSER.parseStringToRegex("(a|b)*(a|b*)")
        val bracketsB = PARSER.parseStringToRegex("(ab|c)(a(b|c))")
        val bracketsC = PARSER.parseStringToRegex("(ab*)((ab)*)")

        assertEquals("(?(|(*b*),a|),(*(|b,a|)*)?)", bracketsA)
        assertEquals("(?(?(|c,b|),a?),(|c,(?b,a?)|)?)", bracketsB)
        assertEquals("(?(*(?b,a?)*),(?(*b*),a?)?)", bracketsC)
    }

    @Test
    fun emptyTest() {
        assertEquals("EMP", PARSER.parseStringToRegex(""))
    }

    @Test
    fun specialSymbolsTest() {
        val polLower = PARSER.parseStringToRegex("\\l")
        val polUpper = PARSER.parseStringToRegex("\\u")
        val numbers = PARSER.parseStringToRegex("\\d")
        val special = PARSER.parseStringToRegex("\\c")

        assertEquals("(&abcdefghijklmnopqrstuvwxyzóąćęłńśźż&)", polLower)
        assertEquals("(&ABCDEFGHIJKLMNOPQRSTUVWXYZÓĄĆĘŁŃŚŹŻ&)", polUpper)
        assertEquals("(&0123456789&)", numbers)
        assertEquals("(&!%&()*+,-./:;<=>?^_{|}~&)", special)

        for (c in "\\?|()[]*") {
            assertEquals(c.toString(), PARSER.parseStringToRegex("\\" + c))
        }
    }
}
