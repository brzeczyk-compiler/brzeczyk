package compiler.parser

import compiler.lexer.Location
import compiler.parser.grammar.Production

interface ParseTree<S : Comparable<S>> {
    val start: Location
    val end: Location
    val symbol: S

    data class Branch<S : Comparable<S>>(
        override val start: Location,
        override val end: Location,
        override val symbol: S,
        val children: List<ParseTree<S>>,
        val production: Production<S>
    ) : ParseTree<S>

    data class Leaf<S : Comparable<S>>(
        override val start: Location,
        override val end: Location,
        override val symbol: S,
        val content: String
    ) : ParseTree<S>
}
