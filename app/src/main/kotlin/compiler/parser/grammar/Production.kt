package compiler.parser.grammar
import compiler.common.regex.Regex

class Production<S : Comparable<S>> {
    val lhs: S
        get() = TODO()

    val rhs: Regex<S>
        get() = TODO()
}
