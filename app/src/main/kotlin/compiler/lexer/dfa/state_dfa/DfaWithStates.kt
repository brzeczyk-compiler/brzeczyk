package compiler.lexer.dfa.state_dfa

import compiler.lexer.dfa.Dfa

interface DfaWithStates<A, R> : Dfa<A, R> {
    val startState: DfaState<A, R>
}
