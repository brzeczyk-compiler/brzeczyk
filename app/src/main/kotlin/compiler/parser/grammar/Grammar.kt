package compiler.parser.grammar

data class Grammar<S : Comparable<S>> (val start: S, val productions: Collection<Production<S>>)
