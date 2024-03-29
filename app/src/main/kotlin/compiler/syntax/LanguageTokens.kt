package compiler.syntax

import compiler.dfa.AbstractDfa
import compiler.regex.RegexDfa
import compiler.syntax.utils.TokenRegexParser

object LanguageTokens {
    fun getTokens(): List<Pair<AbstractDfa<Char, Unit>, TokenType>> {
        val list = listOf(
            // Parenthesis, braces and brackets
            Pair(TokenType.LEFT_PAREN, "\\("),
            Pair(TokenType.RIGHT_PAREN, "\\)"),
            Pair(TokenType.LEFT_BRACE, "\\{"),
            Pair(TokenType.RIGHT_BRACE, "\\}"),
            Pair(TokenType.LEFT_BRACKET, "\\["),
            Pair(TokenType.RIGHT_BRACKET, "\\]"),

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
            Pair(TokenType.FOREIGN1, "zewnętrzna"),
            Pair(TokenType.FOREIGN2, "zewnętrzny"),
            Pair(TokenType.AS, "jako"),

            // Array related keywords
            Pair(TokenType.LENGTH, "długość"),
            Pair(TokenType.ARRAY_ALLOCATION, "ciąg"),
            Pair(TokenType.ARRAY_FOR_EACH, "dla"),
            Pair(TokenType.IN, "wewnątrz"),

            // Generators related keywords
            Pair(TokenType.GENERATOR, "przekaźnik"),
            Pair(TokenType.YIELD, "przekaż"),
            Pair(TokenType.FOR_EACH, "otrzymując"),
            Pair(TokenType.FROM, "od"),

            // Special characters
            Pair(TokenType.COLON, ":"),
            Pair(TokenType.SEMICOLON, ";"),
            Pair(TokenType.QUESTION_MARK, "\\?"),
            Pair(TokenType.COMMA, ","),
            Pair(TokenType.ARROW, "->"),
            Pair(TokenType.NEWLINE, "\n"),

            // Arithmetic operators
            Pair(TokenType.PLUS, "+"),
            Pair(TokenType.MINUS, "-"),
            Pair(TokenType.MULTIPLY, "\\*"),
            Pair(TokenType.DIVIDE, "/"),
            Pair(TokenType.MODULO, "%"),

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
            Pair(TokenType.IFF, "wtw"),
            Pair(TokenType.XOR, "albo"),

            // Boolean constants
            Pair(TokenType.TRUE_CONSTANT, "prawda"),
            Pair(TokenType.FALSE_CONSTANT, "fałsz"),
            Pair(TokenType.UNIT_CONSTANT, "nic"),

            // Integer literals
            Pair(TokenType.INTEGER, """\d\d*"""),

            // String literals
            Pair(TokenType.STRING, "„([\\\\\\l\\u\\d\\c„ \t]|(\\\\”))*”"),

            // Identifiers - names for functions and variables
            // Have to start with a lowercase letter
            // Can include alphanumeric characters and underscore
            Pair(TokenType.IDENTIFIER, """\l[\l\u\d_]*"""),

            // Foreign names - external names of imported foreign functions
            // Enclosed in backticks to avoid confusion with keywords and types
            Pair(TokenType.FOREIGN_NAME, """`[\l\u_][\l\u\d_]*`"""),

            // Type identifiers - names of types
            // Includes built in types Liczba, Czy, Nic and Napis
            Pair(TokenType.TYPE_INTEGER, "Liczba"),
            Pair(TokenType.TYPE_BOOLEAN, "Czy"),
            Pair(TokenType.TYPE_UNIT, "Nic"),
            Pair(TokenType.TYPE_STRING, "Wypowiedź"),

            // Whitespace and comments
            // Technically not real tokens
            // Should be filtered out before syntax analysis
            Pair(TokenType.IGNORED, "([ \t]*)|(//[ \t\\l\\u\\d\\c„”]*)")
        )

        return list.map {
            Pair(RegexDfa(TokenRegexParser.parseStringToRegex(it.second)), it.first)
        }
    }
}
