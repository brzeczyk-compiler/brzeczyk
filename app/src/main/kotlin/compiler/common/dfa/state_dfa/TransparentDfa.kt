package compiler.common.dfa.state_dfa

import compiler.common.dfa.Dfa

interface TransparentDfa<A, R> : Dfa<A, R> {
    val startState: DfaState<A, R>

    // TODO: possibly create default implementation or override just in RegexDfa instead
    // override fun newWalk(): DfaWalk<A, R> {}
}
