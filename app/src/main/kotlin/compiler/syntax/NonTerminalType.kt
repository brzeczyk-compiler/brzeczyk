package compiler.syntax

enum class NonTerminalType {
    // root of parse tree
    PROGRAM,
    // types, literals
    TYPE, CONST,
    // argument lists
    DEF_ARGS, DEF_ARG,
    CALL_ARGS,
    // generators related
    GENERATOR_ITERATION, ARRAY_ITERATION, MANY_ITERATIONS,
    // variable/function declaration
    VAR_DECL, FUNC_DEF, FOREIGN_DECL,
    // normal expression categories
    EXPR, EXPR2, EXPR4, EXPR8, EXPR16, EXPR32, EXPR64, EXPR128, EXPR256, EXPR512, EXPR1024, EXPR2048, EXPR4096,
    // enclosed expression categories
    E_EXPR, E_EXPR2, E_EXPR4, E_EXPR8, E_EXPR16, E_EXPR32, E_EXPR64, E_EXPR128, E_EXPR256, E_EXPR512, E_EXPR1024, E_EXPR2048, E_EXPR4096,
    // statements
    STATEMENT, NON_BRACE_STATEMENT, NON_IF_NON_BRACE_STATEMENT, ATOMIC_STATEMENT,
    // multiple statements
    MAYBE_BLOCK, NON_IF_MAYBE_BLOCK, MANY_STATEMENTS
}
