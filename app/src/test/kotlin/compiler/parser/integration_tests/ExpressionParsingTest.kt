package compiler.parser.integration_tests

import compiler.common.regex.RegexFactory
import compiler.lexer.lexer_grammar.RegexParser
import compiler.parser.ParseTree
import compiler.parser.Parser
import compiler.parser.grammar.Grammar
import compiler.parser.grammar.Production
import org.junit.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpressionParsingTest : ParserTest() {
    private val mulExpr = 'A'
    private val addExpr = 'B'
    private val numExpr = 'C'

    private val addToMul = Production(addExpr, RegexParser.parseStringToRegex("$addExpr(\\+$mulExpr)*"))
    private val mulToNum = Production(mulExpr, RegexParser.parseStringToRegex("$mulExpr(\\*$numExpr)*"))
    private val numParentheses = Production(numExpr, RegexParser.parseStringToRegex("\\(${addExpr}\\)"))
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

    @Ignore @Test
    fun `test parse tree for 0`() {
        val grammar = getExpressionGrammar()
        val parser = Parser(grammar, getMockedDiagnostics())
        val parseTree = parser.process(leafSequence("0"))
        assertEquals(branch(addToMul, branch(mulToNum, branch(numToZero, leaf('0', 0)))), parseTree)
    }

    @Ignore @Test
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
                    leaf('0', 0)
                )
            ),
            leaf('+', 1),
            branch(
                mulToNum,
                branch(
                    numToOne,
                    leaf('1', 2)
                )
            )
        )

        assertEquals(expectedParseTree, parseTree)
    }

    @Ignore @Test
    fun `test parse tree for (0+1+0) mul 1+0`() {
        val grammar = getExpressionGrammar()
        val parser = Parser(grammar, getMockedDiagnostics())
        val parseTree = parser.process(leafSequence("(0+1+0)*1+0"))

        val sumInParentheses = branch(
            numParentheses,
            leaf('(', 0),
            branch(
                addToMul,
                branch(mulToNum, branch(numToZero, leaf('0', 1))),
                leaf('+', 2),
                branch(mulToNum, branch(numToOne, leaf('1', 3))),
                leaf('+', 4),
                branch(mulToNum, branch(numToZero, leaf('0', 5))),
            ),
            leaf(')', 6)
        )

        val expectedParseTree = branch(
            addToMul,
            branch(
                mulToNum,
                sumInParentheses,
                leaf('*', 7),
                branch(numToOne, leaf('1', 8))
            ),
            leaf('+', 9),
            branch(
                mulToNum,
                branch(numToZero, leaf('0', 10))
            )
        )

        assertEquals(expectedParseTree, parseTree)
    }

    @Ignore @Test
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
                        leaf('(', expr.start.row - 1),
                        expr,
                        leaf(')', expr.end.row + 1)
                    )
                )
            )
        }

        val expectedParseTree = encloseWithParentheses(
            encloseWithParentheses(
                encloseWithParentheses(
                    branch(addToMul, branch(mulToNum, branch(numToOne, leaf('1', 3))))
                )
            )
        )

        assertEquals(expectedParseTree, parseTree)
    }

    @Ignore @Test
    fun `test parsing fails for ()`() {
        val grammar = getExpressionGrammar()
        val diagnostics = getMockedDiagnostics()
        val parser = Parser(grammar, diagnostics)
        parser.process(leafSequence("()"))
        assertEquals(1, diagnostics.loggedErrors)
        assertEquals(0, diagnostics.loggedWarnings)
    }

    @Ignore @Test
    fun `test parsing fails for empty sequence`() {
        val grammar = getExpressionGrammar()
        val diagnostics = getMockedDiagnostics()
        val parser = Parser(grammar, diagnostics)
        parser.process(leafSequence(""))
        assertEquals(1, diagnostics.loggedErrors)
        assertEquals(0, diagnostics.loggedWarnings)
    }
}
