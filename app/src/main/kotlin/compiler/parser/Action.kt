package compiler.parser

import compiler.parser.grammar.Production

sealed class Action<S : Comparable<S>> {
    class Shift<S : Comparable<S>> : Action<S>()

    class Call<S : Comparable<S>>(val child: S) : Action<S>()

    class Reduce<S : Comparable<S>>(val production: Production<S>) : Action<S>()
}
