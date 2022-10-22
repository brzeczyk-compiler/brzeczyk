package compiler.parser.analysis

import compiler.common.dfa.state_dfa.DfaState
import compiler.parser.grammar.AutomatonGrammar
import compiler.parser.grammar.Production
import java.util.LinkedList
import java.util.Queue
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class GrammarAnalysis<S : Comparable<S>> {
    fun computeNullable(grammar: AutomatonGrammar<S>): Set<S> {
        return TODO()
    }

    fun computeFirst(grammar: AutomatonGrammar<S>, nullable: Set<S>): Map<S, Set<S>> {
        return TODO()
    }

    fun computeFollow(grammar: AutomatonGrammar<S>, nullable: Set<S>, first: Map<S, Set<S>>): Map<S, Set<S>> {

        val result: MutableMap<S, MutableSet<S>> = HashMap()

        var wasResultUpdated = true
        fun update(setToUpdate: MutableSet<S>, secondSet: Set<S>) {
            val sizeBefore = setToUpdate.size
            setToUpdate.addAll(secondSet)
            if (sizeBefore < setToUpdate.size) wasResultUpdated = true
        }

        fun initializeResult() {
            for (lhs in grammar.productions.keys) result[lhs] = HashSet()
            val statesFirstMap: MutableMap<S, MutableMap<DfaState<S, Production<S>>, MutableSet<S>>> = HashMap()
            while (wasResultUpdated) {
                wasResultUpdated = false
                for ((lhs, dfa) in grammar.productions) {
                    val statesFirst = statesFirstMap.getOrPut(lhs) { HashMap() }
                    val acceptingStates = dfa.getAcceptingStates()
                    val visited: MutableSet<DfaState<S, Production<S>>> = acceptingStates.toHashSet()
                    val queue: Queue<DfaState<S, Production<S>>> = LinkedList()
                    queue.addAll(acceptingStates)

                    while (!queue.isEmpty()) {
                        val current = queue.remove()
                        for ((symbol, predecessors) in dfa.getPredecessors(current))
                            for (prev in predecessors) {
                                update(statesFirst.getOrPut(prev) { HashSet() }, first.getOrDefault(symbol, HashSet()))
                                if (symbol in nullable) update(statesFirst[prev]!!, statesFirst.getOrDefault(current, HashSet()))
                                update(result.getOrPut(symbol) { HashSet() }, statesFirst.getOrDefault(current, HashSet()))
                                if (prev !in visited) {
                                    visited.add(prev)
                                    queue.add(prev)
                                }
                            }
                    }
                }
            }
        }

        val symbolsToPossibleEndSymbolsMap: MutableMap<S, MutableSet<S>> = HashMap()

        fun findAllPossibleEndSymbolsInProductions() {
            for (lhs in grammar.productions.keys) symbolsToPossibleEndSymbolsMap[lhs] = HashSet()
            for ((lhs, dfa) in grammar.productions) {
                val acceptingStates = dfa.getAcceptingStates()
                val visited: MutableSet<DfaState<S, Production<S>>> = acceptingStates.toHashSet()
                val queue: Queue<DfaState<S, Production<S>>> = LinkedList()
                queue.addAll(acceptingStates)

                while (!queue.isEmpty()) {
                    val current = queue.remove()
                    for ((symbol, predecessors) in dfa.getPredecessors(current)) {
                        if (predecessors.isNotEmpty()) symbolsToPossibleEndSymbolsMap.getOrPut(lhs) { HashSet() }.add(symbol)
                        if (symbol in nullable) {
                            for (prev in predecessors) {
                                if (prev !in visited) {
                                    visited.add(prev)
                                    queue.add(prev)
                                }
                            }
                        }
                    }
                }
            }
        }

        fun calculateResult() {
            wasResultUpdated = true
            while (wasResultUpdated) {
                wasResultUpdated = false

                for (lhs in grammar.productions.keys) {
                    for (symbol in symbolsToPossibleEndSymbolsMap.getOrPut(lhs) { HashSet() }) {
                        update(result.getOrPut(symbol) { HashSet() }, result.getOrPut(lhs) { HashSet() })
                    }
                }
            }
        }

        initializeResult()
        findAllPossibleEndSymbolsInProductions()
        calculateResult()

        return result
    }
}
