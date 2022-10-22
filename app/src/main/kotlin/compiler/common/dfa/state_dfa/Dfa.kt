package compiler.common.dfa.state_dfa

import compiler.common.dfa.AbstractDfa

interface Dfa<A, R> : AbstractDfa<A, R> {
    val startState: DfaState<A, R>

    // TODO: possibly create default implementation or override just in RegexDfa instead
    // override fun newWalk(): DfaWalk<A, R> {}
    private fun dfs(visit: (DfaState<A, R>) -> Unit) {
        val visited: MutableSet<DfaState<A, R>> = HashSet()
        fun dfs(state: DfaState<A, R>) {
            if (!visited.add(state)) return
            visit(state)
            for ((_, neighbour) in state.possibleSteps) dfs(neighbour)
        }
        dfs(startState)
    }

    fun getPredecessors(state: DfaState<A, R>): Map<A, Collection<DfaState<A, R>>> {
        val predecessors: MutableMap<A, HashSet<DfaState<A, R>>> = HashMap()
        dfs { visitedState ->
            for ((edge, neighbour) in visitedState.possibleSteps) {
                if (neighbour == state) {
                    predecessors.getOrPut(edge) { HashSet() }.add(visitedState)
                }
            }
        }
        return predecessors
    }

    fun getAcceptingStates(): Collection<DfaState<A, R>> {
        val acceptingStates: MutableSet<DfaState<A, R>> = HashSet()
        dfs { visitedState -> visitedState.result?.let { acceptingStates.add(visitedState) } }
        return acceptingStates
    }
}
