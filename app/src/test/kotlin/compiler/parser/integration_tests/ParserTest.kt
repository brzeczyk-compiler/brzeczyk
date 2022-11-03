package compiler.parser.integration_tests

import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.lexer.Location
import compiler.parser.ParseTree
import compiler.parser.grammar.Grammar
import compiler.parser.grammar.Production

abstract class ParserTest {
    abstract fun getExpressionGrammar(): Grammar<Char>

    class TestDiagnostics : Diagnostics {
        var loggedErrors = 0
        var loggedWarnings = 0
        override fun report(diagnostic: Diagnostic) {
            if (diagnostic.isError())
                loggedErrors += 1
            else
                loggedWarnings += 1
        }
    }

    fun getMockedDiagnostics(): TestDiagnostics {
        return TestDiagnostics()
    }

    fun leaf(symbol: Char, row: Int): ParseTree<Char> {
        return object : ParseTree<Char>(Location(row, 0), Location(row, 0), symbol) {}
    }

    fun branch(production: Production<Char>, vararg children: ParseTree<Char>): ParseTree<Char> {
        return ParseTree.Branch(
            children.first().start,
            children.last().end,
            production.lhs,
            children.asList(),
            production
        )
    }

    fun leafSequence(s: String): Sequence<ParseTree<Char>> {
        return s.asSequence().mapIndexed { idx, symbol -> leaf(symbol, idx) }
    }
}
