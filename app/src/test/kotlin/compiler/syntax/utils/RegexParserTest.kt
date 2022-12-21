package compiler.syntax.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

internal class RegexParserTest {

    class TestRegexParser : AbstractRegexParser<String>() {
        public override fun performStar(child: String): String {
            return "(*$child*)"
        }

        public override fun performConcat(left: String, right: String): String {
            return "(?$left,$right?)"
        }

        public override fun performUnion(left: String, right: String): String {
            return "(|$left,$right|)"
        }

        public override fun getEmpty(): String {
            return "EMP"
        }

        public override fun getAtomic(charSet: Set<Char>): String {
            return if (charSet.size == 1) charSet.toList()[0].toString()
            else "(&" + charSet.toList().sorted().joinToString("") + "&)"
        }

        public override fun getSpecialAtomic(string: String): String {
            return when (string.length) {
                1 -> {
                    if (string[0] in SPECIAL_SYMBOLS) "ERROR" // special symbols should be escaped regardless of getSpecialAtomic implementation
                    else getAtomic(LexerRegexParser.SPECIAL_SYMBOLS.getOrDefault(string, setOf(string[0])))
                }
                else -> "(!$string!)"
            }
        }
    }

    companion object {
        val PARSER = TestRegexParser()
    }

    @Test
    fun `test basic operations`() {
        val star = PARSER.parseStringToRegex("a*")
        val concat = PARSER.parseStringToRegex("ab")
        val union = PARSER.parseStringToRegex("a|b")
        val brackets = PARSER.parseStringToRegex("[abc]")
        val optional = PARSER.parseStringToRegex("a?")
        var bracketsExpected = PARSER.getEmpty()
        for (char in "abc") bracketsExpected = PARSER.performUnion(bracketsExpected, char.toString())

        assertEquals("(*a*)", star)
        assertEquals("(?a,b?)", concat)
        assertEquals("(|a,b|)", union)
        assertEquals(bracketsExpected, brackets)
        assertEquals("(|a,(*EMP*)|)", optional)
    }

    @Test
    fun `test concatenation`() {
        val expressionsToConcatenate = listOf("a", "b*", "(x|d)", "{abcd}", "(abc)?", "[dd]", "(d)*", "(xd)")
        val expressionsParsed = expressionsToConcatenate.map { PARSER.parseStringToRegex(it) }

        val concatenationParsed = PARSER.parseStringToRegex(expressionsToConcatenate.joinToString(""))
        val expectedResult = expressionsParsed.reduce { acc, next -> PARSER.performConcat(acc, next) }

        assertEquals(expectedResult, concatenationParsed)
    }

    @Test
    fun `test parentheses`() {
        val regexToExpected = mapOf(
            "(a|b)*" to PARSER.performStar(PARSER.performUnion("a", "b")),
            "a|b*" to PARSER.performUnion("a", PARSER.performStar("b")),
            "ab|c" to PARSER.performUnion(PARSER.performConcat("a", "b"), "c"),
            "a(b|c)" to PARSER.performConcat("a", PARSER.performUnion("b", "c")),
            "ab*" to PARSER.performConcat("a", PARSER.performStar("b")),
            "(ab)*" to PARSER.performStar(PARSER.performConcat("a", "b"))
        )

        for ((regex, expected) in regexToExpected) {
            assertEquals(expected, PARSER.parseStringToRegex(regex))
        }
    }

    @Test
    fun `test empty string`() {
        assertEquals("EMP", PARSER.parseStringToRegex(""))
    }

    @Test
    fun `test special symbols`() {
        val polishLower = PARSER.parseStringToRegex("\\l")
        val polishUpper = PARSER.parseStringToRegex("\\u")
        val numbers = PARSER.parseStringToRegex("\\d")
        val special = PARSER.parseStringToRegex("\\c")
        val specialString = PARSER.parseStringToRegex("{abcd}")

        assertEquals("(&abcdefghijklmnopqrstuvwxyzóąćęłńśźż&)", polishLower)
        assertEquals("(&ABCDEFGHIJKLMNOPQRSTUVWXYZÓĄĆĘŁŃŚŹŻ&)", polishUpper)
        assertEquals("(&0123456789&)", numbers)
        assertEquals("(&!%&()*+,-./:;<=>?^_{|}~&)", special)
        assertEquals("(!abcd!)", specialString)
    }

    @Test
    fun `test escaping characters`() {
        for (c in "\\?|*()[]{}") {
            assertEquals(c.toString(), PARSER.parseStringToRegex("\\" + c))
        }
    }

    @Test
    fun `test exceptions`() {
        for (regex in setOf("abc[d", "abc{d")) {
            val message = assertFails {
                PARSER.parseStringToRegex(regex)
            }
            assertEquals("The bracket at position 3 has no corresponding closing bracket", message.message)
        }
        assertFails {
            PARSER.parseStringToRegex("(a))")
        }
    }
}
