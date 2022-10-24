package compiler.parser.grammar
import compiler.common.regex.Regex

data class Production<S : Comparable<S>> (val lhs: S, val rhs: Regex<S>)
