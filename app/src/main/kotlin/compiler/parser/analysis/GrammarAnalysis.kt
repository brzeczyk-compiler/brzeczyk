package compiler.parser.analysis

import compiler.common.dfa.state_dfa.Dfa
import compiler.common.dfa.state_dfa.DfaState
import compiler.parser.grammar.AutomatonGrammar
import compiler.parser.grammar.Production

class GrammarAnalysis<S : Comparable<S>> {
    fun computeNullable(grammar: AutomatonGrammar<S>): Set<S> {
        val nullable: MutableSet<S> = HashSet()
        val visited: MutableSet<DfaState<S, Production<S>>> = HashSet()
        val stateQueue: ArrayDeque<DfaState<S, Production<S>>> = ArrayDeque()
        val conditionalSets: MutableMap<S, MutableSet<DfaState<S, Production<S>>>> = HashMap()

        grammar.productions.forEach { (_, dfa) -> stateQueue.addAll(dfa.getAcceptingStates()) }
        fun analyzePredecessors(state: DfaState<S, Production<S>>, dfa: Dfa<S, Production<S>>) {
            val predecessorsMap = dfa.getPredecessors(state)
            for ((symbolOnEdge, predecessors) in predecessorsMap) {
                if (symbolOnEdge in nullable) {
                    stateQueue.addAll(predecessors)
                } else {
                    conditionalSets.getOrPut(symbolOnEdge) { HashSet() }.addAll(predecessors)
                }
            }
        }

        while (stateQueue.isNotEmpty()) {
            val state = stateQueue.removeFirst()
            grammar.productions.forEach { (_, dfa) -> analyzePredecessors(state, dfa) }
            // check if this is a startState for some production
            for ((leftSideSymbol, rightSideDfa) in grammar.productions) {
                if (state == rightSideDfa.startState) {
                    nullable.add(leftSideSymbol)
                    conditionalSets[leftSideSymbol]?.let { stateQueue.addAll(it.filter { visited.add(it) }) }
                    conditionalSets[leftSideSymbol]?.clear()
                }
            }
        }
        return nullable
    }

    fun computeFirst(grammar: AutomatonGrammar<S>, nullable: Set<S>): Map<S, Set<S>> {
        val symbols = grammar.productions.keys.toMutableSet()

        // bfs to find all grammar symbols
        for (prod in grammar.productions) {
            val dfa = prod.value
            val visited = emptySet<DfaState<S, Production<S>>>().toMutableSet()
            val queue = ArrayDeque<DfaState<S, Production<S>>>()
            queue.add(dfa.startState)

            while (queue.isNotEmpty()) {
                val state = queue.removeFirst()
                if (visited.contains(state))
                    continue
                visited.add(state)
                symbols.addAll(state.possibleSteps.keys)
                queue.addAll(state.possibleSteps.values)
            }
        }

        val first: MutableMap<S, MutableSet<S>> = HashMap()

        // bfs through nullable edges
        for (symbol in symbols) {
            first[symbol] = mutableSetOf(symbol)
            val dfa = grammar.productions[symbol] ?: continue
            val visited = emptySet<DfaState<S, Production<S>>>().toMutableSet()
            val queue = ArrayDeque<DfaState<S, Production<S>>>()
            queue.add(dfa.startState)

            while (queue.isNotEmpty()) {
                val state = queue.removeFirst()
                if (visited.contains(state))
                    continue
                visited.add(state)
                for ((edgeSymbol, nextState) in state.possibleSteps) {
                    first[symbol]!!.add(edgeSymbol)
                    if (nullable.contains(edgeSymbol))
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
