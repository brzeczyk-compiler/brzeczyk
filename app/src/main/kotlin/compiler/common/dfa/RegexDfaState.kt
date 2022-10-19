package compiler.common.dfa

import compiler.common.dfa.state_dfa.DfaState

class RegexDfaState<A, R> : DfaState<A, R> {
    override val result: R
        get() = TODO("Not yet implemented")

    override val possibleSteps: Map<A, DfaState<A, R>>
        get() = TODO("Not yet implemented")
}
