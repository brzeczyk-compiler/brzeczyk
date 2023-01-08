package compiler.syntax

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.diagnostics.Diagnostic
import compiler.diagnostics.Diagnostics
import compiler.grammar.Production
import compiler.input.Location
import compiler.input.LocationRange
import compiler.parser.ParseTree
import compiler.syntax.LanguageGrammar.Productions
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AstFactoryTest {
    // helper procedures
    private val dummyLocation = Location(0, 0)
    private val dummyLocationRange = LocationRange(dummyLocation, dummyLocation)
    private val dummyDiagnostics = mockk<Diagnostics>()

    private fun makeNTNode(nonTerminalType: NonTerminalType, production: Production<Symbol>, vararg children: ParseTree<Symbol>): ParseTree<Symbol> =
        ParseTree.Branch(dummyLocationRange, Symbol.NonTerminal(nonTerminalType), children.asList(), production)
    private fun makeTNode(tokenType: TokenType, content: String): ParseTree<Symbol> =
        ParseTree.Leaf(dummyLocationRange, Symbol.Terminal(tokenType), content)

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
                Program.Global.VariableDefinition(Variable(Variable.Kind.VALUE, "moja_wartość", Type.Number, null, dummyLocationRange), dummyLocationRange),
                Program.Global.VariableDefinition(Variable(Variable.Kind.VARIABLE, "moja_zmienna", Type.Boolean, null, dummyLocationRange), dummyLocationRange)
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
                            Function.Parameter("x", Type.Boolean, null, dummyLocationRange),
                            Function.Parameter("y", Type.Number, null, dummyLocationRange),
                        ),
                        Type.Number, listOf(), false, dummyLocationRange
                    ),

                    dummyLocationRange
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates generator definition`() {
        val parseTree = makeNTNode(
            NonTerminalType.PROGRAM, Productions.program,
            makeNTNode(
                NonTerminalType.FUNC_DEF, Productions.funcDef,
                makeTNode(TokenType.GENERATOR, "przekaźnik"),
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
                            Function.Parameter("x", Type.Boolean, null, dummyLocationRange),
                            Function.Parameter("y", Type.Number, null, dummyLocationRange),
                        ),
                        Type.Number, listOf(), true, dummyLocationRange
                    ),

                    dummyLocationRange
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates foreign function declarations`() {
        val parseTree = makeNTNode(
            NonTerminalType.PROGRAM, Productions.program,
            makeNTNode(
                NonTerminalType.FOREIGN_DECL, Productions.foreignDecl,
                makeTNode(TokenType.FOREIGN1, "zewnętrzna"),
                makeTNode(TokenType.FUNCTION, "czynność"),
                makeTNode(TokenType.IDENTIFIER, "poboczna0"),
                makeTNode(TokenType.LEFT_PAREN, "("),
                makeNTNode(
                    NonTerminalType.DEF_ARGS, Productions.defArgs1,
                    makeNTNode(
                        NonTerminalType.DEF_ARG, Productions.defArg,
                        makeTNode(TokenType.IDENTIFIER, "x"),
                        makeTNode(TokenType.COLON, ":"),
                        makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_INTEGER, "Liczba"))
                    )
                ),
                makeTNode(TokenType.RIGHT_PAREN, ")"),
            ),
            makeTNode(TokenType.NEWLINE, "\n"),
            makeNTNode(
                NonTerminalType.FOREIGN_DECL, Productions.foreignDecl,
                makeTNode(TokenType.FOREIGN1, "zewnętrzna"),
                makeTNode(TokenType.FUNCTION, "czynność"),
                makeTNode(TokenType.IDENTIFIER, "poboczna1"),
                makeTNode(TokenType.LEFT_PAREN, "("),
                makeNTNode(NonTerminalType.DEF_ARGS, Productions.defArgs1),
                makeTNode(TokenType.RIGHT_PAREN, ")"),
                makeTNode(TokenType.ARROW, "->"),
                makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_INTEGER, "Liczba")),
            ),
            makeTNode(TokenType.NEWLINE, "\n"),
            makeNTNode(
                NonTerminalType.FOREIGN_DECL, Productions.foreignDecl,
                makeTNode(TokenType.FOREIGN1, "zewnętrzna"),
                makeTNode(TokenType.FUNCTION, "czynność"),
                makeTNode(TokenType.FOREIGN_NAME, "`_Poboczna2`"),
                makeTNode(TokenType.LEFT_PAREN, "("),
                makeNTNode(NonTerminalType.DEF_ARGS, Productions.defArgs1),
                makeTNode(TokenType.RIGHT_PAREN, ")"),
                makeTNode(TokenType.AS, "jako"),
                makeTNode(TokenType.IDENTIFIER, "poboczna2")
            ),
            makeTNode(TokenType.SEMICOLON, ";"),
            makeNTNode(
                NonTerminalType.FOREIGN_DECL, Productions.foreignDecl,
                makeTNode(TokenType.FOREIGN1, "zewnętrzna"),
                makeTNode(TokenType.FUNCTION, "czynność"),
                makeTNode(TokenType.FOREIGN_NAME, "`Poboczna3`"),
                makeTNode(TokenType.LEFT_PAREN, "("),
                makeNTNode(NonTerminalType.DEF_ARGS, Productions.defArgs1),
                makeTNode(TokenType.RIGHT_PAREN, ")"),
                makeTNode(TokenType.ARROW, "->"),
                makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_BOOLEAN, "Czy")),
                makeTNode(TokenType.AS, "jako"),
                makeTNode(TokenType.IDENTIFIER, "poboczna3")
            ),
            makeTNode(TokenType.NEWLINE, "\n"),
            makeNTNode(
                NonTerminalType.FOREIGN_DECL, Productions.foreignDecl,
                makeTNode(TokenType.FOREIGN2, "zewnętrzny"),
                makeTNode(TokenType.GENERATOR, "przekaźnik"),
                makeTNode(TokenType.IDENTIFIER, "zew_generator"),
                makeTNode(TokenType.LEFT_PAREN, "("),
                makeNTNode(
                    NonTerminalType.DEF_ARGS, Productions.defArgs1,
                    makeNTNode(
                        NonTerminalType.DEF_ARG, Productions.defArg,
                        makeTNode(TokenType.IDENTIFIER, "x"),
                        makeTNode(TokenType.COLON, ":"),
                        makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_INTEGER, "Liczba"))
                    )
                ),
                makeTNode(TokenType.RIGHT_PAREN, ")"),
                makeTNode(TokenType.ARROW, "->"),
                makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_BOOLEAN, "Czy")),
                makeTNode(TokenType.AS, "jako"),
                makeTNode(TokenType.IDENTIFIER, "mój_generator")
            ),
            makeTNode(TokenType.NEWLINE, "\n"),
            makeNTNode(
                NonTerminalType.FOREIGN_DECL, Productions.foreignDecl,
                makeTNode(TokenType.FOREIGN1, "zewnętrzny"),
                makeTNode(TokenType.FUNCTION, "czynność"),
                makeTNode(TokenType.IDENTIFIER, "odmieniony"),
                makeTNode(TokenType.LEFT_PAREN, "("),
                makeNTNode(NonTerminalType.DEF_ARGS, Productions.defArgs1),
                makeTNode(TokenType.RIGHT_PAREN, ")"),
                makeTNode(TokenType.ARROW, "->"),
                makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_INTEGER, "Liczba")),
            ),
            makeTNode(TokenType.NEWLINE, "\n"),
            makeNTNode(
                NonTerminalType.FOREIGN_DECL, Productions.foreignDecl,
                makeTNode(TokenType.FOREIGN2, "zewnętrzna"),
                makeTNode(TokenType.GENERATOR, "przekaźnik"),
                makeTNode(TokenType.IDENTIFIER, "odmieniona"),
                makeTNode(TokenType.LEFT_PAREN, "("),
                makeNTNode(NonTerminalType.DEF_ARGS, Productions.defArgs1),
                makeTNode(TokenType.RIGHT_PAREN, ")"),
                makeTNode(TokenType.ARROW, "->"),
                makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_INTEGER, "Liczba")),
            ),
        )
        val expectedAst = Program(
            listOf(
                Program.Global.FunctionDefinition(
                    Function(
                        "poboczna0",
                        listOf(Function.Parameter("x", Type.Number, null, dummyLocationRange)),
                        Type.Unit,
                        Function.Implementation.Foreign("poboczna0"),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
                ),
                Program.Global.FunctionDefinition(
                    Function(
                        "poboczna1",
                        listOf(),
                        Type.Number,
                        Function.Implementation.Foreign("poboczna1"),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
                ),
                Program.Global.FunctionDefinition(
                    Function(
                        "poboczna2",
                        listOf(),
                        Type.Unit,
                        Function.Implementation.Foreign("_Poboczna2"),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
                ),
                Program.Global.FunctionDefinition(
                    Function(
                        "poboczna3",
                        listOf(),
                        Type.Boolean,
                        Function.Implementation.Foreign("Poboczna3"),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
                ),
                Program.Global.FunctionDefinition(
                    Function(
                        "mój_generator",
                        listOf(Function.Parameter("x", Type.Number, null, dummyLocationRange)),
                        Type.Boolean,
                        Function.Implementation.Foreign("zew_generator"),
                        true,
                        dummyLocationRange
                    ),
                    dummyLocationRange
                ),
                Program.Global.FunctionDefinition(
                    Function(
                        "odmieniony",
                        listOf(),
                        Type.Number,
                        Function.Implementation.Foreign("odmieniony"),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
                ),
                Program.Global.FunctionDefinition(
                    Function(
                        "odmieniona",
                        listOf(),
                        Type.Number,
                        Function.Implementation.Foreign("odmieniona"),
                        true,
                        dummyLocationRange
                    ),
                    dummyLocationRange
                ),
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test reports error on invalid foreign identifier`() {
        val parseTree = makeNTNode(
            NonTerminalType.PROGRAM, Productions.program,
            makeNTNode(
                NonTerminalType.FOREIGN_DECL, Productions.foreignDecl,
                makeTNode(TokenType.FOREIGN1, "zewnętrzna"),
                makeTNode(TokenType.FUNCTION, "czynność"),
                makeTNode(TokenType.FOREIGN_NAME, "`Poboczna`"),
                makeTNode(TokenType.LEFT_PAREN, "("),
                makeNTNode(NonTerminalType.DEF_ARGS, Productions.defArgs1),
                makeTNode(TokenType.RIGHT_PAREN, ")"),
            ),
            makeTNode(TokenType.NEWLINE, "\n")
        )

        val diagnostics = mockk<Diagnostics>()
        assertFails { AstFactory.createFromParseTree(parseTree, diagnostics) }
        verify(exactly = 1) { diagnostics.report(ofType(Diagnostic.ParserError.ForeignNameAsInvalidIdentifier::class)) }
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
                            Statement.Evaluation(Expression.NumberLiteral(42, dummyLocationRange), dummyLocationRange),
                            Statement.Evaluation(Expression.BooleanLiteral(true, dummyLocationRange), dummyLocationRange)
                        ),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test reports error on integer overflow`() {
        val parseTree = makeProgramWithExpressionsEvaluation(
            makeNTNode(
                NonTerminalType.EXPR2048, Productions.expr2048Const,
                makeNTNode(NonTerminalType.CONST, Productions.const, makeTNode(TokenType.INTEGER, "9223372036854775808"))
            ) wrapUpTo NonTerminalType.EXPR
        )

        val diagnostics = mockk<Diagnostics>()
        every { diagnostics.report(any()) } returns Unit
        AstFactory.createFromParseTree(parseTree, diagnostics)
        verify(exactly = 1) { diagnostics.report(ofType(Diagnostic.ParserError.InvalidNumberLiteral::class)) }
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
                                Expression.UnaryOperation(Expression.UnaryOperation.Kind.NOT, Expression.Variable("x", dummyLocationRange), dummyLocationRange),
                                dummyLocationRange,
                            ),
                            Statement.Evaluation(
                                Expression.UnaryOperation(Expression.UnaryOperation.Kind.MINUS, Expression.Variable("y", dummyLocationRange), dummyLocationRange),
                                dummyLocationRange,
                            )
                        ),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
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
                                    Expression.BinaryOperation(Expression.BinaryOperation.Kind.ADD, Expression.Variable("x", dummyLocationRange), Expression.Variable("y", dummyLocationRange), dummyLocationRange),
                                    Expression.BinaryOperation(Expression.BinaryOperation.Kind.MULTIPLY, Expression.Variable("z", dummyLocationRange), Expression.Variable("t", dummyLocationRange), dummyLocationRange),
                                    dummyLocationRange,
                                ),
                                dummyLocationRange
                            )
                        ),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
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
                                    Expression.Variable("x", dummyLocationRange),
                                    Expression.Variable("y", dummyLocationRange),
                                    Expression.Variable("z", dummyLocationRange),
                                    dummyLocationRange,
                                ),
                                dummyLocationRange
                            )
                        ),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
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

        val diagnostics = mockk<Diagnostics>()
        assertFails { AstFactory.createFromParseTree(parseTree, diagnostics) }
        verify(exactly = 1) { diagnostics.report(ofType(Diagnostic.ParserError.UnexpectedToken::class)) }
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
                                        Expression.FunctionCall.Argument(null, Expression.Variable("x", dummyLocationRange), dummyLocationRange),
                                        Expression.FunctionCall.Argument("y", Expression.Variable("z", dummyLocationRange), dummyLocationRange),
                                    ),
                                    dummyLocationRange
                                ),
                                dummyLocationRange
                            )
                        ),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
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

        val diagnostics = mockk<Diagnostics>()
        assertFails { AstFactory.createFromParseTree(parseTree, diagnostics) }
        verify(exactly = 1) { diagnostics.report(ofType(Diagnostic.ParserError.UnexpectedToken::class)) }
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
                                Expression.Variable("x", dummyLocationRange),
                                listOf(Statement.LoopBreak(dummyLocationRange)),
                                null,
                                dummyLocationRange,
                            )
                        ),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
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
                                Expression.BinaryOperation(Expression.BinaryOperation.Kind.EQUALS, Expression.Variable("x", dummyLocationRange), Expression.Variable("y", dummyLocationRange), dummyLocationRange),
                                listOf(Statement.LoopBreak(dummyLocationRange)),
                                listOf(
                                    Statement.Conditional(
                                        Expression.BinaryOperation(Expression.BinaryOperation.Kind.EQUALS, Expression.Variable("y", dummyLocationRange), Expression.Variable("x", dummyLocationRange), dummyLocationRange),
                                        listOf(Statement.LoopContinuation(dummyLocationRange)),
                                        listOf(
                                            Statement.Conditional(
                                                Expression.BinaryOperation(Expression.BinaryOperation.Kind.NOT_EQUALS, Expression.Variable("x", dummyLocationRange), Expression.Variable("y", dummyLocationRange), dummyLocationRange),
                                                listOf(Statement.FunctionReturn(Expression.UnitLiteral(dummyLocationRange), true, dummyLocationRange)),
                                                listOf(Statement.FunctionReturn(Expression.Variable("z", dummyLocationRange), false, dummyLocationRange)),
                                                dummyLocationRange,
                                            )
                                        ),
                                        dummyLocationRange
                                    )
                                ),
                                dummyLocationRange
                            )
                        ),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates while loop`() {
        val parseTree = makeProgramWithMainFunction(
            makeNTNode(
                NonTerminalType.STATEMENT, Productions.statementNonBrace,
                makeNTNode(
                    NonTerminalType.NON_BRACE_STATEMENT, Productions.nonBraceStatementWhile,
                    makeTNode(TokenType.WHILE, "dopóki"),
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
                            Statement.Loop(
                                Expression.Variable("x", dummyLocationRange),
                                listOf(Statement.LoopBreak(dummyLocationRange)),
                                dummyLocationRange,
                            )
                        ),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }

    @Test fun `test correctly translates foreach loop`() {
        val parseTree = makeProgramWithMainFunction(
            makeNTNode(
                NonTerminalType.STATEMENT, Productions.statementNonBrace,
                makeNTNode(
                    NonTerminalType.NON_BRACE_STATEMENT, Productions.nonBraceStatementForEach,
                    makeTNode(TokenType.FOR_EACH, "otrzymując"),
                    makeTNode(TokenType.IDENTIFIER, "x"),
                    makeTNode(TokenType.COLON, ":"),
                    makeNTNode(NonTerminalType.TYPE, Productions.type, makeTNode(TokenType.TYPE_INTEGER, "Liczba")),
                    makeTNode(TokenType.FROM, "od"),
                    makeTNode(TokenType.IDENTIFIER, "f"),
                    makeTNode(TokenType.LEFT_PAREN, "("),
                    makeNTNode(
                        NonTerminalType.CALL_ARGS, Productions.callArgs1,
                        makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "a")) wrapUpTo NonTerminalType.EXPR,
                        makeTNode(TokenType.COMMA, ","),
                        makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "b")) wrapUpTo NonTerminalType.EXPR,
                        makeTNode(TokenType.ASSIGNMENT, "="),
                        makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "c")) wrapUpTo NonTerminalType.EXPR,
                    ),
                    makeTNode(TokenType.RIGHT_PAREN, ")"),
                    makeNTNode(
                        NonTerminalType.NON_IF_MAYBE_BLOCK, Productions.nonIfMaybeBlockNonBrace,
                        makeNTNode(
                            NonTerminalType.NON_IF_NON_BRACE_STATEMENT, Productions.nonIfNonBraceStatementAtomic,
                            makeNTNode(
                                NonTerminalType.ATOMIC_STATEMENT, Productions.atomicYield,
                                makeTNode(TokenType.YIELD, "przekaż"),
                                makeNTNode(NonTerminalType.EXPR2048, Productions.expr2048Identifier, makeTNode(TokenType.IDENTIFIER, "x")) wrapUpTo NonTerminalType.EXPR,
                            ),
                            makeTNode(TokenType.NEWLINE, "\n")
                        ),
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
                            Statement.ForeachLoop(
                                Variable(Variable.Kind.VALUE, "x", Type.Number, null, dummyLocationRange),
                                Expression.FunctionCall(
                                    "f",
                                    listOf(
                                        Expression.FunctionCall.Argument(null, Expression.Variable("a", dummyLocationRange), dummyLocationRange),
                                        Expression.FunctionCall.Argument("b", Expression.Variable("c", dummyLocationRange), dummyLocationRange),
                                    ),
                                    dummyLocationRange,
                                ),
                                listOf(Statement.GeneratorYield(Expression.Variable("x", dummyLocationRange), dummyLocationRange)),
                                dummyLocationRange,
                            )
                        ),
                        false,
                        dummyLocationRange
                    ),
                    dummyLocationRange
                )
            )
        )

        val resultAst = AstFactory.createFromParseTree(parseTree, dummyDiagnostics)
        assertEquals(expectedAst, resultAst)
    }
}
