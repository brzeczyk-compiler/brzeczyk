package compiler.syntax

enum class TokenType {
    // Parenthesis and braces
    LEFT_PAREN, RIGHT_PAREN,
    LEFT_BRACE, RIGHT_BRACE,
    // Variable types
    VARIABLE, VALUE, CONSTANT,
    // Control flow
    IF, ELSE_IF, ELSE,
    WHILE, BREAK, CONTINUE,
    // Function related keywords
    RETURN, RETURN_UNIT, FUNCTION,
    // Special characters
    COLON, SEMICOLON, QUESTION_MARK, COMMA, ARROW, NEWLINE,
    // Arithmetic operators
    PLUS, MINUS, MULTIPLY, DIVIDE, MODULO,
    // Bitwise operators
    BIT_NOT, BIT_OR, BIT_AND, BIT_XOR, SHIFT_LEFT, SHIFT_RIGHT,
    // Comparison operators
    EQUAL, NOT_EQUAL, LESS_THAN, LESS_THAN_EQ, GREATER_THAN, GREATER_THAN_EQ,
    // Assignment operator
    ASSIGNMENT,
    // Logical operators
    NOT, OR, AND, IFF, XOR,
    // Named constants
    TRUE_CONSTANT, FALSE_CONSTANT, UNIT_CONSTANT,
    // Integers
    INTEGER,
    // Identifiers
    IDENTIFIER,
    // Built in types
    TYPE_INTEGER, TYPE_BOOLEAN, TYPE_UNIT,
    // Currently, there are no user defined types
    // TYPE_IDENTIFIER,

    // Whitespace and comments
    // Technically not real tokens
    // Should be filtered out before syntax analysis
    TO_IGNORE,
}
