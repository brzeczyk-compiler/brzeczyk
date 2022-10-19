package compiler.parser.grammar

class Grammar<S : Comparable<S>> {
    val start: S
        get() = TODO()
    val productions: Collection<Production<S>>
        get() = TODO()
}
