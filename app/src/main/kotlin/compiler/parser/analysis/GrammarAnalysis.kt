package compiler.parser.analysis

import compiler.common.dfa.state_dfa.DfaState
import compiler.parser.grammar.AutomatonGrammar
import compiler.parser.grammar.Production
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

        var didAnySetChange = true
        fun updateAndCheckIfChanged(setToUpdate: MutableSet<S>, secondSet: Set<S>) {
            val sizeBefore = setToUpdate.size
            setToUpdate.addAll(secondSet)
            if (sizeBefore < setToUpdate.size) didAnySetChange = true
        }

        fun initializeResult() {
            for (lhs in grammar.productions.keys) result[lhs] = HashSet()
            val statesFirstMap: MutableMap<S, MutableMap<DfaState<S, Production<S>>, MutableSet<S>>> = HashMap()
            while (didAnySetChange) {
                didAnySetChange = false
                for ((lhs, dfa) in grammar.productions) {
                    val statesFirst = statesFirstMap.getOrPut(lhs) { HashMap() }
                    val acceptingStates = dfa.getAcceptingStates()
                    val visited: MutableSet<DfaState<S, Production<S>>> = acceptingStates.toHashSet()
                    val queue: ArrayDeque<DfaState<S, Production<S>>> = ArrayDeque()
                    queue.addAll(acceptingStates)

                    while (!queue.isEmpty()) {
                        val current = queue.removeFirst()
                        for ((symbol, predecessors) in dfa.getPredecessors(current))
                            for (prev in predecessors) {
                                updateAndCheckIfChanged(statesFirst.getOrPut(prev) { HashSet() }, first[symbol]!!)
                                if (symbol in nullable) updateAndCheckIfChanged(statesFirst[prev]!!, statesFirst.getOrDefault(current, HashSet()))
                                updateAndCheckIfChanged(result.getOrPut(symbol) { HashSet() }, statesFirst.getOrDefault(current, HashSet()))
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
                val queue: ArrayDeque<DfaState<S, Production<S>>> = ArrayDeque()
                queue.addAll(acceptingStates)

                while (!queue.isEmpty()) {
                    val current = queue.removeFirst()
                    for ((symbol, predecessors) in dfa.getPredecessors(current)) {
                        symbolsToPossibleEndSymbolsMap.getOrPut(lhs) { HashSet() }.add(symbol)
                        if (symbol in nullable) {
                            predecessors.filter { it !in visited }.forEach {
                                visited.add(it)
                                queue.add(it)
                            }
                        }
                    }
                }
            }
        }

        fun calculateResult() {
            didAnySetChange = true
            while (didAnySetChange) {
                didAnySetChange = false

                for (lhs in grammar.productions.keys) {
                    for (symbol in symbolsToPossibleEndSymbolsMap.getOrPut(lhs) { HashSet() }) {
                        updateAndCheckIfChanged(result.getOrPut(symbol) { HashSet() }, result.getOrPut(lhs) { HashSet() })
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
