package compiler.common.dfa

import compiler.common.dfa.state_dfa.Dfa
import compiler.common.dfa.state_dfa.DfaState

class CompositeDfa<S : Comparable<S>, R, IR> (private val components: List<Pair<Dfa<S, IR>, R>>) : Dfa<S, R> {
    class MultipleAcceptingComponentsException : Exception()

    inner class State(private val componentStates: List<DfaState<S, IR>?>) : DfaState<S, R> {
        override val result: R?
            get() {
                val notNullResults = componentStates.withIndex().filter { it.value?.result != null }
                return when (notNullResults.size) {
                    0 -> null
                    1 -> components[notNullResults.first().index].second
                    else -> throw MultipleAcceptingComponentsException()
                }
            }

        override val possibleSteps: Map<S, DfaState<S, R>>
            get() {
                val possibleNextSymbols = componentStates.filterNotNull().map { it.possibleSteps.keys }.reduce { acc, it -> acc union it }
                return possibleNextSymbols.associateWith { symbol -> State(componentStates.map { it?.possibleSteps?.get(symbol) }) }
            }

        override fun equals(other: Any?): Boolean = other is CompositeDfa<*, *, *>.State && componentStates == other.componentStates

        override fun hashCode(): Int = componentStates.hashCode()
    }

    override val startState: DfaState<S, R> = State(components.map { it.first.startState })
}
