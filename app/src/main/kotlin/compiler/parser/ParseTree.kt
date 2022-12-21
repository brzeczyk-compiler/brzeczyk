package compiler.parser

import compiler.grammar.Production
import compiler.input.LocationRange

interface ParseTree<S : Comparable<S>> {
    val location: LocationRange
    val symbol: S

    data class Branch<S : Comparable<S>>(
        override val location: LocationRange,
        override val symbol: S,
        val children: List<ParseTree<S>>,
        val production: Production<S>
    ) : ParseTree<S>

    data class Leaf<S : Comparable<S>>(
        override val location: LocationRange,
        override val symbol: S,
        val content: String
    ) : ParseTree<S>
}
