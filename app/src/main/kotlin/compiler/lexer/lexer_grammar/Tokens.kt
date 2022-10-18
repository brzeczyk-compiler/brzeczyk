package compiler.lexer.lexer_grammar

import compiler.lexer.dfa.Dfa
import compiler.lexer.dfa.RegexDfa
import compiler.lexer.dfa.state_dfa.PlainDfaStateType

class Tokens(private val dfaFactory: DfaFactory = RegexDfaFactory()) {

    interface DfaFactory {
        fun fromRegexString(regexString: String): Dfa<Char, PlainDfaStateType>
    }

    private class RegexDfaFactory : DfaFactory {
        override fun fromRegexString(regexString: String): Dfa<Char, PlainDfaStateType> {
            return RegexDfa(RegexParser.parseStringToRegex(regexString))
        }
    }

    fun getTokens(): List<Pair<TokenType, Dfa<Char, PlainDfaStateType>>> {
        val list = listOf(
            // Parenthesis and braces
            Pair(TokenType.LEFT_PAREN, "\\("),
            Pair(TokenType.RIGHT_PAREN, "\\)"),
            Pair(TokenType.LEFT_BRACE, "{"),
            Pair(TokenType.RIGHT_BRACE, "}"),

            // Variable types
            Pair(TokenType.VARIABLE, "zm"),
            Pair(TokenType.VALUE, "wart"),
            Pair(TokenType.CONSTANT, "stała"),

            // Control flow
            Pair(TokenType.IF, "jeśli"),
            Pair(TokenType.ELSE_IF, "zaś gdy"),
            Pair(TokenType.ELSE, "wpp"),
            Pair(TokenType.WHILE, "dopóki"),
            Pair(TokenType.BREAK, "przerwij"),
            Pair(TokenType.CONTINUE, "pomiń"),

            // Function related keywords
            Pair(TokenType.RETURN, "zwróć"),
            Pair(TokenType.RETURN_UNIT, "zakończ"),
            Pair(TokenType.FUNCTION, "czynność"),

            // Special characters
            Pair(TokenType.COLON, ":"),
            Pair(TokenType.SEMICOLON, ";"),
            Pair(TokenType.QUESTION_MARK, "\\?"),
            Pair(TokenType.COMMA, ","),
            Pair(TokenType.NEWLINE, "\n"),

            // Arithmetic operators
            Pair(TokenType.PLUS, "+"),
            Pair(TokenType.MINUS, "-"),
            Pair(TokenType.MULTIPLY, "\\*"),
            Pair(TokenType.DIVIDE, "/"),
            Pair(TokenType.MODULO, "%"),

            // Increment and decrement operators
            Pair(TokenType.INCREMENT, "++"),
            Pair(TokenType.DECREMENT, "--"),

            // Bitwise operators
            Pair(TokenType.BIT_NOT, "~"),
            Pair(TokenType.BIT_OR, "\\|"),
            Pair(TokenType.BIT_AND, "&"),
            Pair(TokenType.BIT_XOR, "^"),
            Pair(TokenType.SHIFT_LEFT, "<<"),
            Pair(TokenType.SHIFT_RIGHT, ">>"),

            // Comparison operators
            Pair(TokenType.EQUAL, "=="),
            Pair(TokenType.NOT_EQUAL, "!="),
            Pair(TokenType.LESS_THAN, "<"),
            Pair(TokenType.LESS_THAN_EQ, "<="),
            Pair(TokenType.GREATER_THAN, ">"),
            Pair(TokenType.GREATER_THAN_EQ, ">="),

            // Assignment operator
            Pair(TokenType.ASSIGNMENT, "="),

            // Logical operators
            Pair(TokenType.NOT, "nie"),
            Pair(TokenType.OR, "lub"),
            Pair(TokenType.AND, "oraz"),

            // Boolean constants
            Pair(TokenType.TRUE_CONSTANT, "prawda"),
            Pair(TokenType.FALSE_CONSTANT, "fałsz"),

            // Integer literals
            // Only includes nonnegative integers
            Pair(TokenType.INTEGER, """\d\d*"""),

            // Identifiers - names for functions and variables
            // Have to start with a lowercase letter
            // Can include alphanumeric characters and underscore
            Pair(TokenType.IDENTIFIER, """\l[\l\u\d_]*"""),

            // Type identifiers - names of types
            // Includes built in types Liczba, Czy and Nic
            Pair(TokenType.TYPE_INTEGER, "Liczba"),
            Pair(TokenType.TYPE_BOOLEAN, "Czy"),
            Pair(TokenType.TYPE_UNIT, "Nic"),
            // User defined types have to start with uppercase letter
            // Can include alphanumeric characters and underscore
            // Currently unused as there are no user defined types
            // Pair(TokenType.TYPE_IDENTIFIER, """\u[\l\u\d_]*"""),

            // Whitespace and comments
            // Technically not real tokens
            // Should be filtered out before syntax analysis
            Pair(TokenType.TO_IGNORE, "([ \t]*)|(//[ \t\\l\\u\\d\\c]*)")
        )

        return list.map {
            Pair(it.first, dfaFactory.fromRegexString(it.second))
        }
    }
}
