package compiler.common.dfa.state_dfa

interface DfaState<A, R> {
    val result: R?
    val possibleSteps: Map<A, DfaState<A, R>>
}
