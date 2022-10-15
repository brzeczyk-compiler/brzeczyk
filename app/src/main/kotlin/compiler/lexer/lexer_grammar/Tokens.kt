package compiler.lexer.lexer_grammar

import compiler.lexer.dfa.Dfa
import compiler.lexer.dfa.RegexDfa
import compiler.lexer.lexer_grammar.TokenType.*

class Tokens(private val dfaFactory: DfaFactory = RegexDfaFactory()) {

    interface DfaFactory {
        fun fromRegexString(regexString: String): Dfa
    }

    private class RegexDfaFactory : DfaFactory {
        override fun fromRegexString(regexString: String): Dfa {
            return RegexDfa(RegexParser().parseStringToRegex(regexString))
        }
    }

    fun getTokens(): List<Pair<TokenType, Dfa>> {
        val list = listOf(
            // Parenthesis and braces
            Pair(LEFT_PAREN, "\\("),
            Pair(RIGHT_PAREN, "\\)"),
            Pair(LEFT_BRACE, "{"),
            Pair(RIGHT_BRACE, "}"),

            // Variable types
            Pair(VARIABLE, "zm"),
            Pair(VALUE, "wart"),
            Pair(CONSTANT, "stała"),

            // Control flow
            Pair(IF, "jeśli"),
            Pair(ELSE_IF, "zaś gdy"),
            Pair(ELSE, "wpp"),
            Pair(WHILE, "dopóki"),
            Pair(BREAK, "przerwij"),
            Pair(CONTINUE, "pomiń"),

            // Function related keywords
            Pair(RETURN, "zwróć"),
            Pair(RETURN_UNIT, "zakończ"),
            Pair(FUNCTION, "czynność"),

            // Special characters
            Pair(COLON, ":"),
            Pair(SEMICOLON, ";"),
            Pair(QUESTION_MARK, "\\?"),
            Pair(COMMA, ","),
            Pair(NEWLINE, "\n"),

            // Arithmetic operators
            Pair(PLUS, "+"),
            Pair(MINUS, "-"),
            Pair(MULTIPLY, "\\*"),
            Pair(DIVIDE, "/"),
            Pair(MODULO, "%"),

            // Increment and decrement operators
            Pair(INCREMENT, "++"),
            Pair(DECREMENT, "--"),

            // Bitwise operators
            Pair(BIT_NOT, "~"),
            Pair(BIT_OR, "\\|"),
            Pair(BIT_AND, "&"),
            Pair(BIT_XOR, "^"),
            Pair(SHIFT_LEFT, "<<"),
            Pair(SHIFT_RIGHT, ">>"),

            // Comparison operators
            Pair(EQUAL, "=="),
            Pair(NOT_EQUAL, "!="),
            Pair(LESS_THAN, "<"),
            Pair(LESS_THAN_EQ, "<="),
            Pair(GREATER_THAN, ">"),
            Pair(GREATER_THAN_EQ, ">="),

            // Assignment operator
            Pair(ASSIGNMENT, "="),

            // Logical operators
            Pair(NOT, "nie"),
            Pair(OR, "lub"),
            Pair(AND, "oraz"),

            // Boolean constants
            Pair(TRUE_CONSTANT, "prawda"),
            Pair(FALSE_CONSTANT, "fałsz"),

            // Integer literals
            // Only includes nonnegative integers
            Pair(INTEGER, """\d\d*"""),

            // Identifiers - names for functions and variables
            // Have to start with a lowercase letter
            // Can include alphanumeric characters and underscore
            Pair(IDENTIFIER, """\l[\l\u\d_]*"""),

            // Type identifiers - names of types
            // Have to start with uppercase letter
            // Can include alphanumeric characters and underscore
            // Includes Liczba, Czy and Nic
            // Maybe built-in types should have their own tokens?
            // Currently, there are no user defined types
            Pair(TYPE_IDENTIFIER, """\u[\l\u\d_]*"""),

            // Whitespace and comments
            // Technically not real tokens
            // Should be filtered out before syntax analysis
            Pair(WHITESPACE, "[ \t]*|//[ \t\\l\\u\\d\\c]*")
        )

        return list.map {
            Pair(it.first, dfaFactory.fromRegexString(it.second))
        }
    }
}