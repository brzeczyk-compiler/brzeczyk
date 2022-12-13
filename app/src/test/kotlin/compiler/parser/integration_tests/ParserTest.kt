package compiler.parser.integration_tests

import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.lexer.Location
import compiler.lexer.LocationRange
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
        override fun hasAnyError() = loggedErrors > 0
    }

    fun getMockedDiagnostics(): TestDiagnostics {
        return TestDiagnostics()
    }

    data class Leaf(override val symbol: Char, val row: Int) : ParseTree<Char> {
        override val location get() = LocationRange(Location(row, 0), Location(row, 0))
    }

    fun branch(production: Production<Char>, vararg children: ParseTree<Char>): ParseTree<Char> {
        return ParseTree.Branch(
            LocationRange(children.first().location.start, children.last().location.end),
            production.lhs,
            children.asList(),
            production
        )
    }

    fun leafSequence(s: String): Sequence<ParseTree<Char>> {
        return s.asSequence().mapIndexed { idx, symbol -> Leaf(symbol, idx) }
    }
}
