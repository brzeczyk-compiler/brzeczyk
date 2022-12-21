package compiler.grammar

import compiler.dfa.CompositeDfa
import compiler.dfa.Dfa
import compiler.regex.RegexDfa

data class AutomatonGrammar<S : Comparable<S>> (val start: S, val productions: Map<S, Dfa<S, Production<S>>>) {

    companion object {
        fun <S : Comparable<S>> createFromGrammar(grammar: Grammar<S>): AutomatonGrammar<S> {
            val combinedProductions = HashMap<S, Dfa<S, Production<S>>>()
            for ((lhs, productions) in grammar.productions.groupBy { it.lhs })
                combinedProductions[lhs] = CompositeDfa(productions.map { Pair(RegexDfa(it.rhs), it) })
            return AutomatonGrammar(grammar.start, combinedProductions)
        }
    }

    fun getSymbols(): Set<S> {
        val symbols = productions.keys.toMutableSet()
        for (prod in productions) {
            val dfa = prod.value
            symbols.addAll(dfa.getEdgeSymbols())
        }
        return symbols
    }
}
