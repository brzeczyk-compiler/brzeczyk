package compiler.syntax

enum class TokenType {
    // Parenthesis, braces and brackets
    LEFT_PAREN, RIGHT_PAREN,
    LEFT_BRACE, RIGHT_BRACE,
    LEFT_BRACKET, RIGHT_BRACKET,
    // Variable types
    VARIABLE, VALUE, CONSTANT,
    // Control flow
    IF, ELSE_IF, ELSE,
    WHILE, BREAK, CONTINUE,
    // Function related keywords
    RETURN, RETURN_UNIT, FUNCTION, FOREIGN1, FOREIGN2, AS,
    // Array related keyword
    LENGTH, ARRAY_ALLOCATION,
    // Generators related keywords
    GENERATOR, YIELD, FOR_EACH, FROM,
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
    // Integer and string literals
    INTEGER, STRING,
    // Identifiers
    IDENTIFIER, FOREIGN_NAME,
    // Built in types
    TYPE_INTEGER, TYPE_BOOLEAN, TYPE_UNIT, TYPE_STRING,

    // Whitespace and comments
    // Technically not real tokens
    // Should be filtered out before syntax analysis
    TO_IGNORE,
}
