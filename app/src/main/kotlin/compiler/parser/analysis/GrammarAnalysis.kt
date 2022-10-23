package compiler.parser.analysis

import compiler.common.dfa.state_dfa.DfaState
import compiler.parser.grammar.AutomatonGrammar
import compiler.parser.grammar.Production

class GrammarAnalysis<S : Comparable<S>> {
    fun computeNullable(grammar: AutomatonGrammar<S>): Set<S> {
        return TODO()
    }

    fun computeFirst(grammar: AutomatonGrammar<S>, nullable: Set<S>): Map<S, Set<S>> {
        val symbols = grammar.productions.keys
        val first = emptyMap<S, MutableSet<S>>().toMutableMap()

        // bfs through nullable edges
        for (symbol in symbols) {
            first[symbol] = mutableSetOf(symbol)
            val dfa = grammar.productions[symbol]!!
            val visited = emptySet<DfaState<S, Production<S>>>()
            val queue = mutableListOf(dfa.startState)

            while (queue.isNotEmpty()) {
                val state = queue.removeAt(0)
                if (visited.contains(state))
                    continue
                for ((edgeSymbol, nextState) in state.possibleSteps) {
                    if (!nullable.contains(edgeSymbol))
                        continue
                    first[symbol]!!.add(edgeSymbol)
                    queue.add(nextState)
                }
            }
        }

        // transitive closure
        for (i in 1..symbols.size) {
            for (symbol in symbols) {
                val nextStepFirst = emptySet<S>().toMutableSet()
                for (nextSymbol in first[symbol]!!) {
                    nextStepFirst.addAll(first[nextSymbol]!!)
                }
                first[symbol]!!.addAll(nextStepFirst)
            }
        }

        return first
    }

    fun computeFollow(grammar: AutomatonGrammar<S>, nullable: Set<S>, first: Map<S, Set<S>>): Map<S, Set<S>> {
        return TODO()
    }
}
