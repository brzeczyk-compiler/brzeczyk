package compiler.common.dfa.state_dfa

import compiler.common.dfa.AbstractDfa
import compiler.common.dfa.DfaWalk

interface Dfa<A, R> : AbstractDfa<A, R> {
    val startState: DfaState<A, R>

    override fun newWalk(): DfaWalk<A, R> {
        return object : DfaWalk<A, R> {
            private var currentState: DfaState<A, R>? = startState
            override fun getAcceptingStateTypeOrNull(): R? {
                return currentState?.result
            }

            override fun isDead(): Boolean {
                return currentState == null || (currentState?.result == null && currentState?.possibleSteps?.isEmpty() == true)
            }

            override fun step(a: A) {
                currentState = currentState?.possibleSteps?.get(a)
            }
        }
    }
}
