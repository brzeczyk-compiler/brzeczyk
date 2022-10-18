package compiler.common.dfa.state_dfa

import compiler.common.dfa.Dfa

interface DfaWithStates<A, R> : Dfa<A, R> {
    val startState: DfaState<A, R>
}
