package compiler.lexer.dfa

import compiler.lexer.dfa.state_dfa.DfaState

class RegexDfaState<A, R> : DfaState<A, R> {
    override val result: R
        get() = TODO("Not yet implemented")

    override fun getPossibleSteps(): Map<A, DfaState<A, R>> {
        TODO("Not yet implemented")
    }
}
