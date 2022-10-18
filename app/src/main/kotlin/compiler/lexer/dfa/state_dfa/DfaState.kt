package compiler.lexer.dfa.state_dfa

interface DfaState<A, R> {
    val result: R
    fun getPossibleSteps(): Map<A, DfaState<A, R>>
}
