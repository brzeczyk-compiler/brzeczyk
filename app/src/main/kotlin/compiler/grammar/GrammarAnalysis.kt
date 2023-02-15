package compiler.grammar

import compiler.dfa.Dfa
import compiler.dfa.DfaState
import kotlin.collections.HashMap
import kotlin.collections.HashSet

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
                    conditionalSets[leftSideSymbol]?.let { set -> stateQueue.addAll(set.filter { visited.add(it) }) }
                    conditionalSets[leftSideSymbol]?.clear()
                }
            }
        }
        return nullable
    }

    fun computeFirst(grammar: AutomatonGrammar<S>, nullable: Set<S>): Map<S, Set<S>> {
        val symbols = grammar.getSymbols()
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

    fun computeFirstPlus(nullable: Set<S>, first: Map<S, Set<S>>, follow: Map<S, Set<S>>): Map<S, Set<S>> {
        return first.keys.associateWith { symbol ->
            if (nullable.contains(symbol))
                first[symbol]!!.union(follow[symbol]!!)
            else
                first[symbol]!!
        }
    }
}
