package compiler.lexer.lexer_grammar

import compiler.lexer.dfa.Dfa
import compiler.lexer.dfa.DfaWalk
import compiler.lexer.lexer_grammar.TokenType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TokensTest {

    private class MockDfaWalk(val regex : Regex) : DfaWalk {
        private var currentString = StringBuilder()

        override fun isAccepted(): Boolean {
            return regex.matches(currentString.toString())
        }

        override fun isDead(): Boolean {
            return false;
        }

        override fun step(a: Char) {
            currentString.append(a)
        }
    }

    private class MockDfa(regexString: String) : Dfa {
        private val regex : Regex;

        init {
            // Change the format of regex to one used by Kotlin
            // Escape special characters not used in our parser
            // Replace our special characters with their counterparts
            val filtered : String = regexString
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
            // Remove nested square brackets
            var level = 0
            val pattern = StringBuilder()
            for (character in filtered) {
                when (character) {
                    '[' -> {
                        if (level == 0) pattern.append('[')
                        level += 1
                    }
                    ']' -> {
                        level -= 1
                        if (level == 0) pattern.append(']')
                    }
                    else -> pattern.append(character)
                }
            }
            regex = Regex(pattern.toString())
        }

        override fun newWalk(): DfaWalk {
            return MockDfaWalk(regex)
        }
    }

    private class MockDfaFactory : Tokens.DfaFactory {
        override fun fromRegexString(regexString: String): Dfa {
            return MockDfa(regexString)
        }
    }

    private fun accepts(dfa: Dfa, string: String): Boolean {
        val walk = dfa.newWalk()
        for (character in string) {
            walk.step(character)
        }
        return walk.isAccepted()
    }

    private fun firstMatch(tokens: List<Pair<TokenType, Dfa>>, string: String) : TokenType? {
        for ((type, dfa) in tokens) {
            if (accepts(dfa, string))
                return type
        }
        return null
    }

    @Test fun keywordsTest() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, "jeśli"), IF)
        assertEquals(firstMatch(tokens, "zaś gdy"), ELSE_IF)
        assertEquals(firstMatch(tokens, "wpp"), ELSE)
        assertEquals(firstMatch(tokens, "dopóki"), WHILE)
        assertEquals(firstMatch(tokens, "przerwij"), BREAK)
        assertEquals(firstMatch(tokens, "pomiń"), CONTINUE)
        assertEquals(firstMatch(tokens, "czynność"), FUNCTION)
        assertEquals(firstMatch(tokens, "zwróć"), RETURN)
        assertEquals(firstMatch(tokens, "zakończ"), RETURN_UNIT)
        assertEquals(firstMatch(tokens, "zm"), VARIABLE)
        assertEquals(firstMatch(tokens, "wart"), VALUE)
        assertEquals(firstMatch(tokens, "stała"), CONSTANT)
        assertEquals(firstMatch(tokens, "prawda"), TRUE_CONSTANT)
        assertEquals(firstMatch(tokens, "fałsz"), FALSE_CONSTANT)
    }

    @Test fun specialSymbolsTest() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, "("), LEFT_PAREN)
        assertEquals(firstMatch(tokens, ")"), RIGHT_PAREN)
        assertEquals(firstMatch(tokens, "{"), LEFT_BRACE)
        assertEquals(firstMatch(tokens, "}"), RIGHT_BRACE)
        assertEquals(firstMatch(tokens, ":"), COLON)
        assertEquals(firstMatch(tokens, ","), COMMA)
        assertEquals(firstMatch(tokens, ";"), SEMICOLON)
        assertEquals(firstMatch(tokens, "\n"), NEWLINE)
        assertEquals(firstMatch(tokens, "?"), QUESTION_MARK)
    }

    @Test fun operatorsTest() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, "+"), PLUS)
        assertEquals(firstMatch(tokens, "-"), MINUS)
        assertEquals(firstMatch(tokens, "*"), MULTIPLY)
        assertEquals(firstMatch(tokens, "/"), DIVIDE)
        assertEquals(firstMatch(tokens, "%"), MODULO)
        assertEquals(firstMatch(tokens, "++"), INCREMENT)
        assertEquals(firstMatch(tokens, "--"), DECREMENT)
        assertEquals(firstMatch(tokens, "~"), BIT_NOT)
        assertEquals(firstMatch(tokens, "|"), BIT_OR)
        assertEquals(firstMatch(tokens, "&"), BIT_AND)
        assertEquals(firstMatch(tokens, "^"), BIT_XOR)
        assertEquals(firstMatch(tokens, "<<"), SHIFT_LEFT)
        assertEquals(firstMatch(tokens, ">>"), SHIFT_RIGHT)
        assertEquals(firstMatch(tokens, "=="), EQUAL)
        assertEquals(firstMatch(tokens, "!="), NOT_EQUAL)
        assertEquals(firstMatch(tokens, "<"), LESS_THAN)
        assertEquals(firstMatch(tokens, "<="), LESS_THAN_EQ)
        assertEquals(firstMatch(tokens, ">"), GREATER_THAN)
        assertEquals(firstMatch(tokens, ">="), GREATER_THAN_EQ)
        assertEquals(firstMatch(tokens, "="), ASSIGNMENT)
        assertEquals(firstMatch(tokens, "nie"), NOT)
        assertEquals(firstMatch(tokens, "lub"), OR)
        assertEquals(firstMatch(tokens, "oraz"), AND)

        assertNull(firstMatch(tokens, "&&"))
        assertNull(firstMatch(tokens, "||"))
        assertNull(firstMatch(tokens, "!"))
        assertNull(firstMatch(tokens, "==="))
        assertNull(firstMatch(tokens, "<>"))
        assertNull(firstMatch(tokens, "/="))
    }

    @Test fun integerTest() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, "0"), INTEGER)
        assertEquals(firstMatch(tokens, "1"), INTEGER)
        assertEquals(firstMatch(tokens, "3"), INTEGER)
        assertEquals(firstMatch(tokens, "5934"), INTEGER)
        assertEquals(firstMatch(tokens, "1234567890"), INTEGER)
        assertEquals(firstMatch(tokens, "00343"), INTEGER)
        assertEquals(firstMatch(tokens, "000"), INTEGER)

        assertNotEquals(firstMatch(tokens, "-0"), INTEGER)
        assertNotEquals(firstMatch(tokens, "-1"), INTEGER)
        assertNotEquals(firstMatch(tokens, "-234"), INTEGER)
        assertNotEquals(firstMatch(tokens, "-04343"), INTEGER)
        assertNotEquals(firstMatch(tokens, "3.14"), INTEGER)
        assertNotEquals(firstMatch(tokens, ".34"), INTEGER)
        assertNotEquals(firstMatch(tokens, "234."), INTEGER)
        assertNotEquals(firstMatch(tokens, "1.343e-10"), INTEGER)
        assertNotEquals(firstMatch(tokens, "-1e+345"), INTEGER)
        assertNotEquals(firstMatch(tokens, "1324ryhvjx"), INTEGER)
        assertNotEquals(firstMatch(tokens, "12324_343"), INTEGER)
        assertNotEquals(firstMatch(tokens, "1,000,000"), INTEGER)
    }

    @Test fun identifierTest() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, "x"), IDENTIFIER)
        assertEquals(firstMatch(tokens, "i"), IDENTIFIER)
        assertEquals(firstMatch(tokens, "żółć"), IDENTIFIER)
        assertEquals(firstMatch(tokens, "prawd"), IDENTIFIER)
        assertEquals(firstMatch(tokens, "prawda0123456789__"), IDENTIFIER)
        assertEquals(firstMatch(tokens, "fałszerstwo"), IDENTIFIER)
        assertEquals(firstMatch(tokens, "jęśliby"), IDENTIFIER)
        assertEquals(firstMatch(tokens, "pchnąć_w_tę_łódź_jeża_lub_ośm_skrzyń_fig"), IDENTIFIER)
        assertEquals(firstMatch(tokens, "pójdźżeKińTęChmurnośćWGłąbFlaszy"), IDENTIFIER)

        assertEquals(firstMatch(tokens, "Liczba"), TYPE_IDENTIFIER)
        assertEquals(firstMatch(tokens, "Czy"), TYPE_IDENTIFIER)
        assertEquals(firstMatch(tokens, "Nic"), TYPE_IDENTIFIER)
        assertEquals(firstMatch(tokens, "Prawda"), TYPE_IDENTIFIER)
        assertEquals(firstMatch(tokens, "Fałsz"), TYPE_IDENTIFIER)
        assertEquals(firstMatch(tokens, "TypeIdentifier"), TYPE_IDENTIFIER)
        assertEquals(firstMatch(tokens, "Typ_z_cyframi_7639"), TYPE_IDENTIFIER)
        assertEquals(firstMatch(tokens, "W_niżach_mógł_zjeść_truflę_koń_bądź_psy"), TYPE_IDENTIFIER)

        assertNull(firstMatch(tokens, "2erere"))
        assertNull(firstMatch(tokens, "_sdf02"))
        assertNull(firstMatch(tokens, "asdf+34"))
        assertNull(firstMatch(tokens, "do-re-mi"))
        assertNull(firstMatch(tokens, "hmm?"))
    }

    @Test fun whitespaceTest() {
        val tokens = Tokens(MockDfaFactory()).getTokens()

        assertEquals(firstMatch(tokens, " "), WHITESPACE)
        assertEquals(firstMatch(tokens, "     "), WHITESPACE)
        assertEquals(firstMatch(tokens, "\t"), WHITESPACE)
        assertEquals(firstMatch(tokens, "  \t  "), WHITESPACE)
        assertEquals(firstMatch(tokens, "\t \t"), WHITESPACE)
        assertEquals(firstMatch(tokens, "// no comment"), WHITESPACE)
        assertEquals(firstMatch(tokens, "//   \t   \t "), WHITESPACE)
        assertEquals(firstMatch(tokens, "// jeśli (x == 10 oraz (y * 3 != żółć) { asdf = xyz > 3 ? a + b : c % d; }"), WHITESPACE)
        assertEquals(firstMatch(tokens, "// \taąbcćdeęfghijklłmnńoópqrsśtuvwxyzźżAĄBCĆDEĘFGHIJKLŁMNŃOÓPQRSŚTUVWXYZŹŻ0123456789{}(),.<>:;?/+=-_!%^&*|~"), WHITESPACE)

        assertNotEquals(firstMatch(tokens, "      E    "), WHITESPACE)
        assertNotEquals(firstMatch(tokens, " \t \n"), WHITESPACE)
        assertNotEquals(firstMatch(tokens, "// comments end with newline\n"), WHITESPACE)
        assertNotEquals(firstMatch(tokens, "/* no multiline\n\tcomments allowed */"), WHITESPACE)
    }
}