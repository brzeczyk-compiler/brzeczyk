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
    private val dummyNodeLocation = NodeLocation(dummyLocation, dummyLocation)
    private val dummyDiagnostics = Diagnostics { }

    private fun makeNTNode(nonTerminalType: NonTerminalType, production: Production<Symbol>, vararg children: ParseTree<Symbol>): ParseTree<Symbol> =
        ParseTree.Branch(dummyLocation, dummyLocation, Symbol.NonTerminal(nonTerminalType), children.asList(), production)
    private fun makeTNode(tokenType: TokenType, content: String): ParseTree<Symbol> =
        ParseTree.Leaf(dummyLocation, dummyLocation, Symbol.Terminal(tokenType), content)

    private fun makeProgramWithMainFunction(vararg statements: ParseTree<Symbol>) = makeNTNode(
        NonTerminalType.PROGRAM, Productions.program,
        makeNTNode(
            NonTerminalType.FUNC_DEF, Productions.funcDef,
            makeTNode(TokenType.FUNCTION, "czynność"),
            makeTNode(TokenType.IDENTIFIER, "główna"),
            makeTNode(TokenType.LEFT_PAREN, "("),
            makeNTNode(NonTerminalType.DEF_ARGS, Productions.defArgs1),
            makeTNode(TokenType.RIGHT_PAREN, ")"),
            makeTNode(TokenType.LEFT_BRACE, "{"),
            makeNTNode(NonTerminalType.MANY_STATEMENTS, Productions.manyStatements, *statements),
            makeTNode(TokenType.RIGHT_BRACE, "}")
        )
    )
    private fun makeProgramWithExpressionsEvaluation(vararg expressions: ParseTree<Symbol>) = makeProgramWithMainFunction(
        *expressions.map {
            makeNTNode(
                NonTerminalType.STATEMENT, Productions.statementNonBrace,
                makeNTNode(
                    NonTerminalType.NON_BRACE_STATEMENT, Productions.nonBraceStatementAtomic,
                    makeNTNode(NonTerminalType.ATOMIC_STATEMENT, Productions.atomicExpr, it),
                    makeTNode(TokenType.NEWLINE, "\n")
                )
            )
        }.toTypedArray()
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
                Program.Global.VariableDefinition(Variable(Variable.Kind.VALUE, "moja_wartość", Type.Number, null, dummyNodeLocation), dummyNodeLocation),
                Program.Global.VariableDefinition(Variable(Variable.Kind.VARIABLE, "moja_zmienna", Type.Boolean, null, dummyNodeLocation), dummyNodeLocation)
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
                            Function.Parameter("x", Type.Boolean, null, dummyNodeLocation),
                            Function.Parameter("y", Type.Number, null, dummyNodeLocation),
                        ),
                        Type.Number, listOf(), dummyNodeLocation
                    ),
                    dummyNodeLocation
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates constants literals`() {
        val parseTree = makeProgramWithExpressionsEvaluation(
            makeNTNode(
                NonTerminalType.EXPR2048, Productions.expr2048Const,
                makeNTNode(NonTerminalType.CONST, Productions.const, makeTNode(TokenType.INTEGER, "42"))
            ) wrapUpTo NonTerminalType.EXPR,

            makeNTNode(
                NonTerminalType.EXPR2048, Productions.expr2048Const,
                makeNTNode(NonTerminalType.CONST, Productions.const, makeTNode(TokenType.TRUE_CONSTANT, "prawda"))
            ) wrapUpTo NonTerminalType.EXPR
        )
        val expectedAst = Program(
            listOf(
                Program.Global.FunctionDefinition(
                    Function(
                        "główna",
                        listOf(),
                        Type.Unit,
                        listOf(
                            Statement.Evaluation(Expression.NumberLiteral(42, dummyNodeLocation), dummyNodeLocation),
                            Statement.Evaluation(Expression.BooleanLiteral(true, dummyNodeLocation), dummyNodeLocation)
                        ),
                        dummyNodeLocation
                    ),
                    dummyNodeLocation
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates unary expressions`() {
        val parseTree = makeProgramWithExpressionsEvaluation(
            makeNTNode(
                NonTerminalType.EXPR2048, Productions.expr2048UnaryBoolNot,
                makeTNode(TokenType.NOT, "!"),
                makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "x"))
            ) wrapUpTo NonTerminalType.EXPR,

            makeNTNode(
                NonTerminalType.EXPR2048, Productions.expr2048UnaryMinus,
                makeTNode(TokenType.MINUS, "-"),
                makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "y"))
            ) wrapUpTo NonTerminalType.EXPR
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
                                Expression.UnaryOperation(Expression.UnaryOperation.Kind.NOT, Expression.Variable("x", dummyNodeLocation), dummyNodeLocation),
                                dummyNodeLocation,
                            ),
                            Statement.Evaluation(
                                Expression.UnaryOperation(Expression.UnaryOperation.Kind.MINUS, Expression.Variable("y", dummyNodeLocation), dummyNodeLocation),
                                dummyNodeLocation,
                            )
                        ),
                        dummyNodeLocation
                    ),
                    dummyNodeLocation
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates binary expressions`() {
        // expression : x + y + z * t
        val parseTree = makeProgramWithExpressionsEvaluation(
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
                                    Expression.BinaryOperation(Expression.BinaryOperation.Kind.ADD, Expression.Variable("x", dummyNodeLocation), Expression.Variable("y", dummyNodeLocation), dummyNodeLocation),
                                    Expression.BinaryOperation(Expression.BinaryOperation.Kind.MULTIPLY, Expression.Variable("z", dummyNodeLocation), Expression.Variable("t", dummyNodeLocation), dummyNodeLocation),
                                    dummyNodeLocation,
                                ),
                                dummyNodeLocation
                            )
                        ),
                        dummyNodeLocation
                    ),
                    dummyNodeLocation
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates ternary if expression`() {
        val parseTree = makeProgramWithExpressionsEvaluation(
            makeNTNode(
                NonTerminalType.EXPR, Productions.exprTernary,
                makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "x")) wrapUpTo NonTerminalType.EXPR2,
                makeTNode(TokenType.QUESTION_MARK, "?"),
                makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "y")) wrapUpTo NonTerminalType.EXPR2,
                makeTNode(TokenType.COLON, ":"),
                makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "z")) wrapUpTo NonTerminalType.EXPR,
            ) wrapUpTo NonTerminalType.EXPR
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
                                Expression.Conditional(
                                    Expression.Variable("x", dummyNodeLocation),
                                    Expression.Variable("y", dummyNodeLocation),
                                    Expression.Variable("z", dummyNodeLocation),
                                    dummyNodeLocation,
                                ),
                                dummyNodeLocation
                            )
                        ),
                        dummyNodeLocation
                    ),
                    dummyNodeLocation
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test reports error on expression as assignment lhs`() {
        // statement : x + x = y
        val parseTree = makeProgramWithMainFunction(
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

        val reportedDiagnostics = ArrayList<Diagnostic>()
        assertFails { AstFactory.createFromParseTree(parseTree) { reportedDiagnostics.add(it) } }
        assertEquals(1, reportedDiagnostics.size)
        assertTrue(reportedDiagnostics.first() is Diagnostic.ParserError)
    }

    @Test fun `test correctly translates function call`() {
        val parseTree = makeProgramWithExpressionsEvaluation(
            makeNTNode(
                NonTerminalType.EXPR2048, Productions.expr2048Call,
                makeTNode(TokenType.IDENTIFIER, "f"),
                makeTNode(TokenType.LEFT_PAREN, "("),
                makeNTNode(
                    NonTerminalType.CALL_ARGS, Productions.callArgs1,
                    makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "x")) wrapUpTo NonTerminalType.EXPR,
                    makeTNode(TokenType.COMMA, ","),
                    makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "y")) wrapUpTo NonTerminalType.EXPR,
                    makeTNode(TokenType.ASSIGNMENT, "="),
                    makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "z")) wrapUpTo NonTerminalType.EXPR,
                ),
                makeTNode(TokenType.RIGHT_PAREN, ")")
            ) wrapUpTo NonTerminalType.EXPR
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
                                Expression.FunctionCall(
                                    "f",
                                    listOf(
                                        Expression.FunctionCall.Argument(null, Expression.Variable("x", dummyNodeLocation), dummyNodeLocation),
                                        Expression.FunctionCall.Argument("y", Expression.Variable("z", dummyNodeLocation), dummyNodeLocation),
                                    ),
                                    dummyNodeLocation
                                ),
                                dummyNodeLocation
                            )
                        ),
                        dummyNodeLocation
                    ),
                    dummyNodeLocation
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test reports error on incorrect call argument name`() {
        val parseTree = makeProgramWithExpressionsEvaluation(
            makeNTNode(
                NonTerminalType.EXPR2048, Productions.expr2048Call,
                makeTNode(TokenType.IDENTIFIER, "f"),
                makeTNode(TokenType.LEFT_PAREN, "("),
                makeNTNode(
                    NonTerminalType.CALL_ARGS, Productions.callArgs2,
                    makeNTNode(
                        NonTerminalType.EXPR2048, Productions.expr2048UnaryMinus,
                        makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "y"))
                    ) wrapUpTo NonTerminalType.EXPR,
                    makeTNode(TokenType.ASSIGNMENT, "="),
                    makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "z")) wrapUpTo NonTerminalType.EXPR,
                ),
                makeTNode(TokenType.RIGHT_PAREN, ")")
            ) wrapUpTo NonTerminalType.EXPR
        )

        val reportedDiagnostics = ArrayList<Diagnostic>()
        assertFails { AstFactory.createFromParseTree(parseTree) { reportedDiagnostics.add(it) } }
        assertEquals(1, reportedDiagnostics.size)
        assertTrue(reportedDiagnostics.first() is Diagnostic.ParserError)
    }

    @Test fun `test correctly translates if without else`() {
        val parseTree = makeProgramWithMainFunction(
            makeNTNode(
                NonTerminalType.STATEMENT, Productions.statementNonBrace,
                makeNTNode(
                    NonTerminalType.NON_BRACE_STATEMENT, Productions.nonBraceStatementIf,
                    makeTNode(TokenType.IF, "jeśli"),
                    makeTNode(TokenType.LEFT_PAREN, "("),
                    makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "x")) wrapUpTo NonTerminalType.EXPR,
                    makeTNode(TokenType.RIGHT_PAREN, ")"),
                    makeNTNode(
                        NonTerminalType.NON_IF_MAYBE_BLOCK, Productions.nonIfMaybeBlockNonBrace,
                        makeNTNode(
                            NonTerminalType.NON_IF_NON_BRACE_STATEMENT, Productions.nonIfNonBraceStatementAtomic,
                            makeNTNode(NonTerminalType.ATOMIC_STATEMENT, Productions.atomicBreak, makeTNode(TokenType.BREAK, "przerwij")),
                            makeTNode(TokenType.NEWLINE, "\n")
                        )
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
                                Expression.Variable("x", dummyNodeLocation),
                                listOf(Statement.LoopBreak(dummyNodeLocation)),
                                null,
                                dummyNodeLocation,
                            )
                        ),
                        dummyNodeLocation
                    ),
                    dummyNodeLocation
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates if,elif,else sequence`() {
        val parseTree = makeProgramWithMainFunction(
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
        val expectedAst = Program(
            listOf(
                Program.Global.FunctionDefinition(
                    Function(
                        "główna",
                        listOf(),
                        Type.Unit,
                        listOf(
                            Statement.Conditional(
                                Expression.BinaryOperation(Expression.BinaryOperation.Kind.EQUALS, Expression.Variable("x", dummyNodeLocation), Expression.Variable("y", dummyNodeLocation), dummyNodeLocation),
                                listOf(Statement.LoopBreak(dummyNodeLocation)),
                                listOf(
                                    Statement.Conditional(
                                        Expression.BinaryOperation(Expression.BinaryOperation.Kind.EQUALS, Expression.Variable("y", dummyNodeLocation), Expression.Variable("x", dummyNodeLocation), dummyNodeLocation),
                                        listOf(Statement.LoopContinuation(dummyNodeLocation)),
                                        listOf(
                                            Statement.Conditional(
                                                Expression.BinaryOperation(Expression.BinaryOperation.Kind.NOT_EQUALS, Expression.Variable("x", dummyNodeLocation), Expression.Variable("y", dummyNodeLocation), dummyNodeLocation),
                                                listOf(Statement.FunctionReturn(Expression.UnitLiteral(dummyNodeLocation), dummyNodeLocation)),
                                                listOf(Statement.FunctionReturn(Expression.Variable("z", dummyNodeLocation), dummyNodeLocation)),
                                                dummyNodeLocation,
                                            )
                                        ),
                                        dummyNodeLocation
                                    )
                                ),
                                dummyNodeLocation
                            )
                        ),
                        dummyNodeLocation
                    ),
                    dummyNodeLocation
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }
}
