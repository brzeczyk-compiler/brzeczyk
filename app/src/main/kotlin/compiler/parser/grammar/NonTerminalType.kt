package compiler.parser.grammar

enum class NonTerminalType {
    // root of parse tree
    PROGRAM,
    // types, literals
    TYPE, CONST,
    // argument lists
    DEF_ARGS, DEF_ARG,
    CALL_ARGS,
    // variable/function declaration
    VAR_DECL, FUNC_DEF,
    // expression categories
    EXPR, EXPR2, EXPR4, EXPR8, EXPR16, EXPR32, EXPR64, EXPR128, EXPR256, EXPR512, EXPR1024, EXPR2048,
    // statements
    STATEMENT, NON_BRACE_STATEMENT, ATOMIC_STATEMENT,
    // multiple statements
    MAYBE_BLOCK, MANY_STATEMENTS
}
