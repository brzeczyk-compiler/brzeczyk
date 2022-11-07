package compiler.parser

import compiler.parser.grammar.Production

sealed class ParserAction<S : Comparable<S>> {
    class Shift<S : Comparable<S>> : ParserAction<S>()

    class Call<S : Comparable<S>>(val symbol: S) : ParserAction<S>()

    class Reduce<S : Comparable<S>>(val production: Production<S>) : ParserAction<S>()
}
