package compiler.parser

import compiler.grammar.Grammar
import compiler.grammar.Production
import compiler.regex.RegexFactory
import compiler.syntax.utils.TokenRegexParser
import kotlin.test.Test
import kotlin.test.assertEquals

class ParserTest : ParserTestBase() {
    private val mulExpr = 'A'
    private val addExpr = 'B'
    private val numExpr = 'C'

    private val addToMul = Production(addExpr, TokenRegexParser.parseStringToRegex("$mulExpr(\\+$mulExpr)*"))
    private val mulToNum = Production(mulExpr, TokenRegexParser.parseStringToRegex("$numExpr(\\*$numExpr)*"))
    private val numParentheses = Production(numExpr, TokenRegexParser.parseStringToRegex("\\(${addExpr}\\)"))
    private val numToZero = Production(numExpr, RegexFactory.createAtomic(setOf('0')))
    private val numToOne = Production(numExpr, RegexFactory.createAtomic(setOf('1')))

    override fun getExpressionGrammar(): Grammar<Char> {
        return Grammar(
            addExpr,
            listOf(
                addToMul,
                mulToNum,
                numParentheses,
                numToZero,
                numToOne
            ),
        )
    }

    @Test
    fun `test parse tree for 0`() {
        val grammar = getExpressionGrammar()
        val parser = Parser(grammar, getMockedDiagnostics())
        val parseTree = parser.process(leafSequence("0"))
        assertEquals(branch(addToMul, branch(mulToNum, branch(numToZero, Leaf('0', 0)))), parseTree)
    }

    @Test
    fun `test parse tree for 0+1`() {
        val grammar = getExpressionGrammar()
        val parser = Parser(grammar, getMockedDiagnostics())
        val parseTree = parser.process(leafSequence("0+1"))
        val expectedParseTree = branch(
            addToMul,
            branch(
                mulToNum,
                branch(
                    numToZero,
                    Leaf('0', 0)
                )
            ),
            Leaf('+', 1),
            branch(
                mulToNum,
                branch(
                    numToOne,
                    Leaf('1', 2)
                )
            )
        )

        assertEquals(expectedParseTree, parseTree)
    }

    @Test
    fun `test parse tree for (0+1+0) mul 1+0`() {
        val grammar = getExpressionGrammar()
        val parser = Parser(grammar, getMockedDiagnostics())
        val parseTree = parser.process(leafSequence("(0+1+0)*1+0"))

        val sumInParentheses = branch(
            numParentheses,
            Leaf('(', 0),
            branch(
                addToMul,
                branch(mulToNum, branch(numToZero, Leaf('0', 1))),
                Leaf('+', 2),
                branch(mulToNum, branch(numToOne, Leaf('1', 3))),
                Leaf('+', 4),
                branch(mulToNum, branch(numToZero, Leaf('0', 5))),
            ),
            Leaf(')', 6)
        )

        val expectedParseTree = branch(
            addToMul,
            branch(
                mulToNum,
                sumInParentheses,
                Leaf('*', 7),
                branch(numToOne, Leaf('1', 8))
            ),
            Leaf('+', 9),
            branch(
                mulToNum,
                branch(numToZero, Leaf('0', 10))
            )
        )

        assertEquals(expectedParseTree, parseTree)
    }

    @Test
    fun `test parse tree for (((1)))`() {
        val grammar = getExpressionGrammar()
        val parser = Parser(grammar, getMockedDiagnostics())
        val parseTree = parser.process(leafSequence("(((1)))"))

        fun encloseWithParentheses(expr: ParseTree<Char>): ParseTree<Char> {
            return branch(
                addToMul,
                branch(
                    mulToNum,
                    branch(
                        numParentheses,
                        Leaf('(', expr.location.start.row - 1),
                        expr,
                        Leaf(')', expr.location.end.row + 1)
                    )
                )
            )
        }

        val expectedParseTree = encloseWithParentheses(
            encloseWithParentheses(
                encloseWithParentheses(
                    branch(addToMul, branch(mulToNum, branch(numToOne, Leaf('1', 3))))
                )
            )
        )

        assertEquals(expectedParseTree, parseTree)
    }

    @Test
    fun `test parsing fails for ()`() {
        val grammar = getExpressionGrammar()
        val diagnostics = getMockedDiagnostics()
        val parser = Parser(grammar, diagnostics)

        try {
            parser.process(leafSequence("()"))
        } catch (_: Parser.ParsingFailed) { }

        assert(diagnostics.loggedErrors >= 1)
        assert(diagnostics.loggedWarnings == 0)
    }

    @Test
    fun `test parsing fails for empty sequence`() {
        val grammar = getExpressionGrammar()
        val diagnostics = getMockedDiagnostics()
        val parser = Parser(grammar, diagnostics)

        try {
            parser.process(leafSequence(""))
        } catch (_: Parser.ParsingFailed) { }

        assert(diagnostics.loggedErrors >= 1)
        assert(diagnostics.loggedWarnings == 0)
    }
}
