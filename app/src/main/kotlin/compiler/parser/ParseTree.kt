package compiler.parser

import compiler.lexer.Location
import compiler.parser.grammar.Production

abstract class ParseTree<S : Comparable<S>>(val start: Location?, val end: Location?, val symbol: S) {
    class Branch<S : Comparable<S>>(
        start: Location?,
        end: Location?,
        symbol: S,
        val children: List<ParseTree<S>>,
        val production: Production<S>
    ) : ParseTree<S>(start, end, symbol)
}
