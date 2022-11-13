package compiler.ast

import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.lexer.Location
import compiler.lexer.lexer_grammar.TokenType
import compiler.parser.ParseTree
import compiler.parser.grammar.NonTerminalType
import compiler.parser.grammar.ParserGrammar.Productions
import compiler.parser.grammar.Production
import compiler.parser.grammar.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class AstFactoryTest {
    // helper procedures
    private val dummyLocation = Location(0, 0)
    private val dummyDiagnostics = Diagnostics { }

    private fun makeNTNode(nonTerminalType: NonTerminalType, production: Production<Symbol>, vararg children: ParseTree<Symbol>): ParseTree<Symbol> =
        ParseTree.Branch(dummyLocation, dummyLocation, Symbol.NonTerminal(nonTerminalType), children.asList(), production)
    private fun makeTNode(tokenType: TokenType, content: String): ParseTree<Symbol> =
        ParseTree.Leaf(dummyLocation, dummyLocation, Symbol.Terminal(tokenType), content)

    private fun makeProgramWithMainFunction(statements: List<ParseTree<Symbol>>) = makeNTNode(
        NonTerminalType.PROGRAM, Productions.program,
        makeNTNode(
            NonTerminalType.FUNC_DEF, Productions.funcDef,
            makeTNode(TokenType.FUNCTION, "czynność"),
            makeTNode(TokenType.IDENTIFIER, "główna"),
            makeTNode(TokenType.LEFT_PAREN, "("),
            makeNTNode(NonTerminalType.DEF_ARGS, Productions.defArgs1),
            makeTNode(TokenType.RIGHT_PAREN, ")"),
            makeTNode(TokenType.LEFT_BRACE, "{"),
            makeNTNode(NonTerminalType.MANY_STATEMENTS, Productions.manyStatements, *statements.toTypedArray()),
            makeTNode(TokenType.RIGHT_BRACE, "}")
        )
    )

    private infix fun ParseTree<Symbol>.wrapUpTo(topLevel: NonTerminalType): ParseTree<Symbol> {
        val levelSequence = listOf(
            NonTerminalType.EXPR2048, NonTerminalType.EXPR1024, NonTerminalType.EXPR512,
            NonTerminalType.EXPR256, NonTerminalType.EXPR128, NonTerminalType.EXPR64, NonTerminalType.EXPR32,
            NonTerminalType.EXPR16, NonTerminalType.EXPR8, NonTerminalType.EXPR4, NonTerminalType.EXPR2,
            NonTerminalType.EXPR
        )
        val productionSequence = listOf(
            Productions.expr1024PassThrough, Productions.expr512PassThrough,
            Productions.expr256PassThrough, Productions.expr128PassThrough, Productions.expr64PassThrough, Productions.expr32PassThrough,
            Productions.expr16PassThrough, Productions.expr8PassThrough, Productions.expr4PassThrough, Productions.expr2PassThrough,
            Productions.exprPassThrough
        )

        val currentLevel = levelSequence.indexOf((symbol as Symbol.NonTerminal).nonTerminal)
        return if (levelSequence[currentLevel] == topLevel) this else makeNTNode(levelSequence[currentLevel + 1], productionSequence[currentLevel], this) wrapUpTo topLevel
    }

    // tests
    @Test fun `test correctly translates empty parse tree`() {
        val parseTree = makeNTNode(NonTerminalType.PROGRAM, Productions.program)
        val expectedAst = Program(listOf())

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates global variable definitions`() {
        val parseTree = makeNTNode(
            NonTerminalType.PROGRAM, Productions.program,
            makeNTNode(
                NonTerminalType.VAR_DECL, Productions.varDecl,
                makeTNode(TokenType.VALUE, "val"),
                makeTNode(TokenType.IDENTIFIER, "moja_wartość"),
                makeTNode(TokenType.COLON, ":"),
                makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_INTEGER, "Liczba"))
            ),
            makeTNode(TokenType.SEMICOLON, ";"),
            makeNTNode(
                NonTerminalType.VAR_DECL, Productions.varDecl,
                makeTNode(TokenType.VARIABLE, "zm"),
                makeTNode(TokenType.IDENTIFIER, "moja_zmienna"),
                makeTNode(TokenType.COLON, ":"),
                makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_BOOLEAN, "Czy"))
            ),
            makeTNode(TokenType.NEWLINE, "\n")
        )
        val expectedAst = Program(
            listOf(
                Program.Global.VariableDefinition(Variable(Variable.Kind.VALUE, "moja_wartość", Type.Number, null)),
                Program.Global.VariableDefinition(Variable(Variable.Kind.VARIABLE, "moja_zmienna", Type.Boolean, null))
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates function definition`() {
        val parseTree = makeNTNode(
            NonTerminalType.PROGRAM, Productions.program,
            makeNTNode(
                NonTerminalType.FUNC_DEF, Productions.funcDef,
                makeTNode(TokenType.FUNCTION, "czynność"),
                makeTNode(TokenType.IDENTIFIER, "poboczna"),
                makeTNode(TokenType.LEFT_PAREN, "("),
                makeNTNode(
                    NonTerminalType.DEF_ARGS, Productions.defArgs1,
                    makeNTNode(
                        NonTerminalType.DEF_ARG, Productions.defArg,
                        makeTNode(TokenType.IDENTIFIER, "x"),
                        makeTNode(TokenType.COLON, ":"),
                        makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_BOOLEAN, "Czy"))
                    ),
                    makeTNode(TokenType.COMMA, ","),
                    makeNTNode(
                        NonTerminalType.DEF_ARG, Productions.defArg,
                        makeTNode(TokenType.IDENTIFIER, "y"),
                        makeTNode(TokenType.COLON, ":"),
                        makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_INTEGER, "Liczba"))
                    )
                ),
                makeTNode(TokenType.RIGHT_PAREN, ")"),
                makeTNode(TokenType.ARROW, "->"),
                makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_INTEGER, "Liczba")),
                makeTNode(TokenType.LEFT_BRACE, "{"),
                makeNTNode(NonTerminalType.MANY_STATEMENTS, Productions.manyStatements),
                makeTNode(TokenType.RIGHT_BRACE, "}")
            )
        )
        val expectedAst = Program(
            listOf(
                Program.Global.FunctionDefinition(
                    Function(
                        "poboczna",
                        listOf(
                            Function.Parameter("x", Type.Boolean, null),
                            Function.Parameter("y", Type.Number, null)
                        ),
                        Type.Number, listOf()
                    )
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates binary expressions`() {
        // expression : x + y + z * t
        val parseTree = makeProgramWithMainFunction(
            listOf(
                makeNTNode(
                    NonTerminalType.STATEMENT, Productions.statementNonBrace,
                    makeNTNode(
                        NonTerminalType.NON_BRACE_STATEMENT, Productions.nonBraceStatementAtomic,
                        makeNTNode(
                            NonTerminalType.ATOMIC_STATEMENT, Productions.atomicExpr,
                            makeNTNode(
                                NonTerminalType.EXPR512, Productions.expr512Plus,
                                makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "x")) wrapUpTo NonTerminalType.EXPR1024,
                                makeTNode(TokenType.PLUS, "+"),
                                makeNTNode(
                                    NonTerminalType.EXPR512, Productions.expr512Plus,
                                    makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "y")) wrapUpTo NonTerminalType.EXPR1024,
                                    makeTNode(TokenType.PLUS, "+"),
                                    makeNTNode(
                                        NonTerminalType.EXPR1024, Productions.expr1024Multiply,
                                        makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "z")),
                                        makeTNode(TokenType.MULTIPLY, "*"),
                                        makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "t")) wrapUpTo NonTerminalType.EXPR1024,
                                    ) wrapUpTo NonTerminalType.EXPR512
                                )
                            ) wrapUpTo NonTerminalType.EXPR
                        ),
                        makeTNode(TokenType.NEWLINE, "\n")
                    )
                )
            )
        )
        val expectedAst = Program(
            listOf(
                Program.Global.FunctionDefinition(
                    Function(
                        "główna",
                        listOf(),
                        Type.Unit,
                        listOf(
                            Statement.Evaluation(
                                Expression.BinaryOperation(
                                    Expression.BinaryOperation.Kind.ADD,
                                    Expression.BinaryOperation(Expression.BinaryOperation.Kind.ADD, Expression.Variable("x"), Expression.Variable("y")),
                                    Expression.BinaryOperation(Expression.BinaryOperation.Kind.MULTIPLY, Expression.Variable("z"), Expression.Variable("t")),
                                )
                            )
                        )
                    )
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test reports error on expression as assignment lhs`() {
        // statement : x + x = y
        val parseTree = makeProgramWithMainFunction(
            listOf(
                makeNTNode(
                    NonTerminalType.STATEMENT, Productions.statementNonBrace,
                    makeNTNode(
                        NonTerminalType.NON_BRACE_STATEMENT, Productions.nonBraceStatementAtomic,
                        makeNTNode(
                            NonTerminalType.ATOMIC_STATEMENT, Productions.atomicAssignment,
                            makeNTNode(
                                NonTerminalType.EXPR512, Productions.expr512Plus,
                                makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "x")) wrapUpTo NonTerminalType.EXPR1024,
                                makeTNode(TokenType.PLUS, "+"),
                                makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "x")) wrapUpTo NonTerminalType.EXPR512
                            ) wrapUpTo NonTerminalType.EXPR,
                            makeTNode(TokenType.ASSIGNMENT, "="),
                            makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "y")) wrapUpTo NonTerminalType.EXPR
                        )
                    )
                )
            )
        )

        val reportedDiagnostics = ArrayList<Diagnostic>()
        assertFails { AstFactory.createFromParseTree(parseTree) { reportedDiagnostics.add(it) } }
        assertEquals(1, reportedDiagnostics.size)
        assertTrue(reportedDiagnostics.first() is Diagnostic.ParserError)
    }

    @Test fun `test correctly translates if,elif,else sequence`() {
        val parseTree = makeProgramWithMainFunction(
            listOf(
                makeNTNode(
                    NonTerminalType.STATEMENT, Productions.statementNonBrace,
                    makeNTNode(
                        NonTerminalType.NON_BRACE_STATEMENT, Productions.nonBraceStatementIf,
                        makeTNode(TokenType.IF, "jeśli"),
                        makeTNode(TokenType.LEFT_PAREN, "("),
                        makeNTNode(
                            NonTerminalType.EXPR16, Productions.expr16Equal,
                            makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "x")) wrapUpTo NonTerminalType.EXPR32,
                            makeTNode(TokenType.EQUAL, "=="),
                            makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "y")) wrapUpTo NonTerminalType.EXPR16
                        ) wrapUpTo NonTerminalType.EXPR,
                        makeTNode(TokenType.RIGHT_PAREN, ")"),
                        makeNTNode(
                            NonTerminalType.NON_IF_MAYBE_BLOCK, Productions.nonIfMaybeBlockNonBrace,
                            makeNTNode(
                                NonTerminalType.NON_IF_NON_BRACE_STATEMENT, Productions.nonIfNonBraceStatementAtomic,
                                makeNTNode(NonTerminalType.ATOMIC_STATEMENT, Productions.atomicBreak, makeTNode(TokenType.BREAK, "przerwij")),
                                makeTNode(TokenType.NEWLINE, "\n")
                            )
                        ),

                        makeTNode(TokenType.ELSE_IF, "zaś gdy"),
                        makeTNode(TokenType.LEFT_PAREN, "("),
                        makeNTNode(
                            NonTerminalType.EXPR16, Productions.expr16Equal,
                            makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "y")) wrapUpTo NonTerminalType.EXPR32,
                            makeTNode(TokenType.EQUAL, "=="),
                            makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "x")) wrapUpTo NonTerminalType.EXPR16
                        ) wrapUpTo NonTerminalType.EXPR,
                        makeTNode(TokenType.RIGHT_PAREN, ")"),
                        makeNTNode(
                            NonTerminalType.NON_IF_MAYBE_BLOCK, Productions.nonIfMaybeBlockNonBrace,
                            makeNTNode(
                                NonTerminalType.NON_IF_NON_BRACE_STATEMENT, Productions.nonIfNonBraceStatementAtomic,
                                makeNTNode(NonTerminalType.ATOMIC_STATEMENT, Productions.atomicContinue, makeTNode(TokenType.CONTINUE, "pomiń")),
                                makeTNode(TokenType.NEWLINE, "\n")
                            )
                        ),

                        makeTNode(TokenType.ELSE_IF, "zaś gdy"),
                        makeTNode(TokenType.LEFT_PAREN, "("),
                        makeNTNode(
                            NonTerminalType.EXPR16, Productions.expr16NotEqual,
                            makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "x")) wrapUpTo NonTerminalType.EXPR32,
                            makeTNode(TokenType.NOT_EQUAL, "!="),
                            makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "y")) wrapUpTo NonTerminalType.EXPR16
                        ) wrapUpTo NonTerminalType.EXPR,
                        makeTNode(TokenType.RIGHT_PAREN, ")"),
                        makeNTNode(
                            NonTerminalType.NON_IF_MAYBE_BLOCK, Productions.nonIfMaybeBlockNonBrace,
                            makeNTNode(
                                NonTerminalType.NON_IF_NON_BRACE_STATEMENT, Productions.nonIfNonBraceStatementAtomic,
                                makeNTNode(NonTerminalType.ATOMIC_STATEMENT, Productions.atomicReturnUnit, makeTNode(TokenType.RETURN_UNIT, "zakończ")),
                                makeTNode(TokenType.NEWLINE, "\n")
                            )
                        ),

                        makeTNode(TokenType.ELSE, "wpp"),
                        makeNTNode(
                            NonTerminalType.NON_IF_MAYBE_BLOCK, Productions.nonIfMaybeBlockNonBrace,
                            makeNTNode(
                                NonTerminalType.NON_IF_NON_BRACE_STATEMENT, Productions.nonIfNonBraceStatementAtomic,
                                makeNTNode(
                                    NonTerminalType.ATOMIC_STATEMENT, Productions.atomicReturn,
                                    makeTNode(TokenType.RETURN, "zwróć"),
                                    makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "z")) wrapUpTo NonTerminalType.EXPR
                                ),
                                makeTNode(TokenType.NEWLINE, "\n")
                            )
                        ),
                    )
                )
            )
        )
        val expectedAst = Program(
            listOf(
                Program.Global.FunctionDefinition(
                    Function(
                        "główna",
                        listOf(),
                        Type.Unit,
                        listOf(
                            Statement.Conditional(
                                Expression.BinaryOperation(Expression.BinaryOperation.Kind.EQUALS, Expression.Variable("x"), Expression.Variable("y")),
                                listOf(Statement.LoopBreak),
                                listOf(
                                    Statement.Conditional(
                                        Expression.BinaryOperation(Expression.BinaryOperation.Kind.EQUALS, Expression.Variable("y"), Expression.Variable("x")),
                                        listOf(Statement.LoopContinuation),
                                        listOf(
                                            Statement.Conditional(
                                                Expression.BinaryOperation(Expression.BinaryOperation.Kind.NOT_EQUALS, Expression.Variable("x"), Expression.Variable("y")),
                                                listOf(Statement.FunctionReturn(Expression.UnitLiteral)),
                                                listOf(Statement.FunctionReturn(Expression.Variable("z")))
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }
}
