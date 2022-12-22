package compiler.dfa

interface Dfa<A, R> : AbstractDfa<A, R> {
    val startState: DfaState<A, R>

    override fun newWalk(): DfaWalk<A, R> {
        return object : DfaWalk<A, R> {
            private var currentState: DfaState<A, R>? = startState
            override fun getAcceptingStateTypeOrNull(): R? {
                return currentState?.result
            }

            override fun isDead(): Boolean {
                var canAccept = false
                currentState?.let {
                    dfs(it) { visitedState ->
                        canAccept = canAccept || (visitedState.result != null)
                    }
                }
                return !canAccept
            }

            override fun step(a: A) {
                currentState = currentState?.possibleSteps?.get(a)
            }
        }
    }

    private fun dfs(
        start: DfaState<A, R> = startState,
        inspectSymbol: (A) -> Unit = {},
        visit: (DfaState<A, R>) -> Unit
    ) {
        val visited: MutableSet<DfaState<A, R>> = HashSet()
        fun dfs(state: DfaState<A, R>) {
            if (!visited.add(state)) return
            visit(state)
            for ((symbol, _) in state.possibleSteps)
                inspectSymbol(symbol)
            for ((_, neighbour) in state.possibleSteps)
                dfs(neighbour)
        }
        dfs(start)
    }

    fun getStates(): Set<DfaState<A, R>> {
        val states: MutableSet<DfaState<A, R>> = HashSet()
        dfs { states.add(it) }
        return states
    }

    fun getEdgeSymbols(): Set<A> {
        val symbols: MutableSet<A> = HashSet()
        dfs(visit = { }, inspectSymbol = { edge -> symbols.add(edge) })
        return symbols
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
