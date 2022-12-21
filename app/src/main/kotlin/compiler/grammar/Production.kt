package compiler.grammar
import compiler.regex.Regex

data class Production<S : Comparable<S>> (val lhs: S, val rhs: Regex<S>)
