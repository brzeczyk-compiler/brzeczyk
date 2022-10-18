package compiler.lexer.lexer_grammar

import compiler.common.dfa.Dfa
import compiler.common.dfa.DfaWalk
import compiler.common.dfa.state_dfa.PlainDfaStateType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TokensTest {

    // Uses Kotlin regex to simulate a DFA
    // builds the current string with each step
    // and tries to match it to regex when isAccepted is called
    private class MockDfaWalk(val regex: Regex) : DfaWalk<Char, PlainDfaStateType> {
        private var currentString = StringBuilder()

        override fun getResult(): PlainDfaStateType {
            if (regex.matches(currentString.toString()))
                return PlainDfaStateType.ACCEPTING
            return PlainDfaStateType.NON_ACCEPTING
        }

        override fun isDead(): Boolean {
            return false
        }

        override fun step(a: Char) {
            currentString.append(a)
        }
    }

    private class MockDfa(regexString: String) : Dfa<Char, PlainDfaStateType> {
        // Kotlin regex equivalent to regexString
        private val regex: Regex

        init {
            // Change the format of regex to one used by Kotlin
            // Escape special characters not used in our parser
            // Replace our special characters with their counterparts
            regex = Regex(
                regexString
                    .replace("{", "\\{")
                    .replace("}", "\\}")
                    .replace("+", "\\+")
                    .replace("-", "\\-")
                    .replace("/", "\\/")
                    .replace("^", "\\^")
                    .replace("\\l", "[a-ząćęłńóśźż]")
                    .replace("\\u", "[A-ZĄĆĘŁŃÓŚŹŻ]")
                    .replace("\\d", "[0-9]")
                    .replace("\\c", """[\{\}\(\),\.<>:;\?\/\+=\-_!%\^&\*\|~]""")
            )
        }

        override fun newWalk(): DfaWalk<Char, PlainDfaStateType> {
            return MockDfaWalk(regex)
        }
    }

    private class MockDfaFactory : Tokens.DfaFactory {
        override fun fromRegexString(regexString: String): Dfa<Char, PlainDfaStateType> {
            return MockDfa(regexString)
        }
    }

    private fun accepts(dfa: Dfa<Char, PlainDfaStateType>, string: String): Boolean {
        val walk = dfa.newWalk()
        string.forEach { walk.step(it) }
        return walk.getResult() == PlainDfaStateType.ACCEPTING
    }

    private fun firstMatch(tokens: List<Pair<TokenType, Dfa<Char, PlainDfaStateType>>>, string: String): TokenType? {
        for ((type, dfa) in tokens) {
            if (accepts(dfa, string))
                return type
        }
        return null
    }

    @Test fun `test keywords`() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, "jeśli"), TokenType.IF)
        assertEquals(firstMatch(tokens, "zaś gdy"), TokenType.ELSE_IF)
        assertEquals(firstMatch(tokens, "wpp"), TokenType.ELSE)
        assertEquals(firstMatch(tokens, "dopóki"), TokenType.WHILE)
        assertEquals(firstMatch(tokens, "przerwij"), TokenType.BREAK)
        assertEquals(firstMatch(tokens, "pomiń"), TokenType.CONTINUE)
        assertEquals(firstMatch(tokens, "czynność"), TokenType.FUNCTION)
        assertEquals(firstMatch(tokens, "zwróć"), TokenType.RETURN)
        assertEquals(firstMatch(tokens, "zakończ"), TokenType.RETURN_UNIT)
        assertEquals(firstMatch(tokens, "zm"), TokenType.VARIABLE)
        assertEquals(firstMatch(tokens, "wart"), TokenType.VALUE)
        assertEquals(firstMatch(tokens, "stała"), TokenType.CONSTANT)
        assertEquals(firstMatch(tokens, "prawda"), TokenType.TRUE_CONSTANT)
        assertEquals(firstMatch(tokens, "fałsz"), TokenType.FALSE_CONSTANT)
    }

    @Test fun `test special symbols`() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, "("), TokenType.LEFT_PAREN)
        assertEquals(firstMatch(tokens, ")"), TokenType.RIGHT_PAREN)
        assertEquals(firstMatch(tokens, "{"), TokenType.LEFT_BRACE)
        assertEquals(firstMatch(tokens, "}"), TokenType.RIGHT_BRACE)
        assertEquals(firstMatch(tokens, ":"), TokenType.COLON)
        assertEquals(firstMatch(tokens, ","), TokenType.COMMA)
        assertEquals(firstMatch(tokens, ";"), TokenType.SEMICOLON)
        assertEquals(firstMatch(tokens, "\n"), TokenType.NEWLINE)
        assertEquals(firstMatch(tokens, "?"), TokenType.QUESTION_MARK)
    }

    @Test fun `test operators`() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, "+"), TokenType.PLUS)
        assertEquals(firstMatch(tokens, "-"), TokenType.MINUS)
        assertEquals(firstMatch(tokens, "*"), TokenType.MULTIPLY)
        assertEquals(firstMatch(tokens, "/"), TokenType.DIVIDE)
        assertEquals(firstMatch(tokens, "%"), TokenType.MODULO)
        assertEquals(firstMatch(tokens, "++"), TokenType.INCREMENT)
        assertEquals(firstMatch(tokens, "--"), TokenType.DECREMENT)
        assertEquals(firstMatch(tokens, "~"), TokenType.BIT_NOT)
        assertEquals(firstMatch(tokens, "|"), TokenType.BIT_OR)
        assertEquals(firstMatch(tokens, "&"), TokenType.BIT_AND)
        assertEquals(firstMatch(tokens, "^"), TokenType.BIT_XOR)
        assertEquals(firstMatch(tokens, "<<"), TokenType.SHIFT_LEFT)
        assertEquals(firstMatch(tokens, ">>"), TokenType.SHIFT_RIGHT)
        assertEquals(firstMatch(tokens, "=="), TokenType.EQUAL)
        assertEquals(firstMatch(tokens, "!="), TokenType.NOT_EQUAL)
        assertEquals(firstMatch(tokens, "<"), TokenType.LESS_THAN)
        assertEquals(firstMatch(tokens, "<="), TokenType.LESS_THAN_EQ)
        assertEquals(firstMatch(tokens, ">"), TokenType.GREATER_THAN)
        assertEquals(firstMatch(tokens, ">="), TokenType.GREATER_THAN_EQ)
        assertEquals(firstMatch(tokens, "="), TokenType.ASSIGNMENT)
        assertEquals(firstMatch(tokens, "nie"), TokenType.NOT)
        assertEquals(firstMatch(tokens, "lub"), TokenType.OR)
        assertEquals(firstMatch(tokens, "oraz"), TokenType.AND)

        assertNull(firstMatch(tokens, "&&"))
        assertNull(firstMatch(tokens, "||"))
        assertNull(firstMatch(tokens, "!"))
        assertNull(firstMatch(tokens, "==="))
        assertNull(firstMatch(tokens, "<>"))
        assertNull(firstMatch(tokens, "/="))
    }

    @Test fun `test integers`() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, "0"), TokenType.INTEGER)
        assertEquals(firstMatch(tokens, "1"), TokenType.INTEGER)
        assertEquals(firstMatch(tokens, "3"), TokenType.INTEGER)
        assertEquals(firstMatch(tokens, "5934"), TokenType.INTEGER)
        assertEquals(firstMatch(tokens, "1234567890"), TokenType.INTEGER)
        assertEquals(firstMatch(tokens, "00343"), TokenType.INTEGER)
        assertEquals(firstMatch(tokens, "000"), TokenType.INTEGER)

        assertNotEquals(firstMatch(tokens, "-0"), TokenType.INTEGER)
        assertNotEquals(firstMatch(tokens, "-1"), TokenType.INTEGER)
        assertNotEquals(firstMatch(tokens, "-234"), TokenType.INTEGER)
        assertNotEquals(firstMatch(tokens, "-04343"), TokenType.INTEGER)
        assertNotEquals(firstMatch(tokens, "3.14"), TokenType.INTEGER)
        assertNotEquals(firstMatch(tokens, ".34"), TokenType.INTEGER)
        assertNotEquals(firstMatch(tokens, "234."), TokenType.INTEGER)
        assertNotEquals(firstMatch(tokens, "1.343e-10"), TokenType.INTEGER)
        assertNotEquals(firstMatch(tokens, "-1e+345"), TokenType.INTEGER)
        assertNotEquals(firstMatch(tokens, "1324ryhvjx"), TokenType.INTEGER)
        assertNotEquals(firstMatch(tokens, "12324_343"), TokenType.INTEGER)
        assertNotEquals(firstMatch(tokens, "1,000,000"), TokenType.INTEGER)
    }

    @Test fun `test identifiers and built-in types`() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, "x"), TokenType.IDENTIFIER)
        assertEquals(firstMatch(tokens, "i"), TokenType.IDENTIFIER)
        assertEquals(firstMatch(tokens, "żółć"), TokenType.IDENTIFIER)
        assertEquals(firstMatch(tokens, "prawd"), TokenType.IDENTIFIER)
        assertEquals(firstMatch(tokens, "prawda0123456789__"), TokenType.IDENTIFIER)
        assertEquals(firstMatch(tokens, "fałszerstwo"), TokenType.IDENTIFIER)
        assertEquals(firstMatch(tokens, "jęśliby"), TokenType.IDENTIFIER)
        assertEquals(firstMatch(tokens, "pchnąć_w_tę_łódź_jeża_lub_ośm_skrzyń_fig"), TokenType.IDENTIFIER)
        assertEquals(firstMatch(tokens, "pójdźżeKińTęChmurnośćWGłąbFlaszy"), TokenType.IDENTIFIER)

        assertEquals(firstMatch(tokens, "Liczba"), TokenType.TYPE_INTEGER)
        assertEquals(firstMatch(tokens, "Czy"), TokenType.TYPE_BOOLEAN)
        assertEquals(firstMatch(tokens, "Nic"), TokenType.TYPE_UNIT)

        assertNull(firstMatch(tokens, "Prawda"))
        assertNull(firstMatch(tokens, "Fałsz"))
        assertNull(firstMatch(tokens, "TypeIdentifier"))
        assertNull(firstMatch(tokens, "Typ_z_cyframi_7639"))
        assertNull(firstMatch(tokens, "W_niżach_mógł_zjeść_truflę_koń_bądź_psy"))
        assertNull(firstMatch(tokens, "2erere"))
        assertNull(firstMatch(tokens, "_sdf02"))
        assertNull(firstMatch(tokens, "asdf+34"))
        assertNull(firstMatch(tokens, "do-re-mi"))
        assertNull(firstMatch(tokens, "hmm?"))
    }

    @Test fun `test whitespace and comments`() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, " "), TokenType.TO_IGNORE)
        assertEquals(firstMatch(tokens, "     "), TokenType.TO_IGNORE)
        assertEquals(firstMatch(tokens, "\t"), TokenType.TO_IGNORE)
        assertEquals(firstMatch(tokens, "  \t  "), TokenType.TO_IGNORE)
        assertEquals(firstMatch(tokens, "\t \t"), TokenType.TO_IGNORE)
        assertEquals(firstMatch(tokens, "// no comment"), TokenType.TO_IGNORE)
        assertEquals(firstMatch(tokens, "//   \t   \t "), TokenType.TO_IGNORE)
        assertEquals(firstMatch(tokens, "// jeśli (x == 10 oraz (y * 3 != żółć) { asdf = xyz > 3 ? a + b : c % d; }"), TokenType.TO_IGNORE)
        assertEquals(firstMatch(tokens, "// \taąbcćdeęfghijklłmnńoópqrsśtuvwxyzźżAĄBCĆDEĘFGHIJKLŁMNŃOÓPQRSŚTUVWXYZŹŻ0123456789{}(),.<>:;?/+=-_!%^&*|~"), TokenType.TO_IGNORE)

        assertNotEquals(firstMatch(tokens, "      E    "), TokenType.TO_IGNORE)
        assertNotEquals(firstMatch(tokens, " \t \n"), TokenType.TO_IGNORE)
        assertNotEquals(firstMatch(tokens, "// comments end with newline\n"), TokenType.TO_IGNORE)
        assertNotEquals(firstMatch(tokens, "/* no multiline\n\tcomments allowed */"), TokenType.TO_IGNORE)
    }
}
