package compiler.parser.analysis

import compiler.common.dfa.state_dfa.Dfa
import compiler.common.dfa.state_dfa.DfaState
import compiler.parser.grammar.AutomatonGrammar
import compiler.parser.grammar.Production

class GrammarAnalysis<S : Comparable<S>> {
    fun computeNullable(grammar: AutomatonGrammar<S>): Set<S> {
        val nullable: MutableSet<S> = HashSet()
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
                    conditionalSets[leftSideSymbol]?.let { stateQueue.addAll(it) }
                    conditionalSets[leftSideSymbol]?.clear()
                }
            }
        }
        return nullable
    }

    fun computeFirst(grammar: AutomatonGrammar<S>, nullable: Set<S>): Map<S, Set<S>> {
        return TODO()
    }

    fun computeFollow(grammar: AutomatonGrammar<S>, nullable: Set<S>, first: Map<S, Set<S>>): Map<S, Set<S>> {
        return TODO()
    }
}
