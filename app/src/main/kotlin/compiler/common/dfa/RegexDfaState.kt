package compiler.common.dfa

import compiler.common.dfa.state_dfa.DfaState
import compiler.common.regex.Regex

data class RegexDfaState<A : Comparable<A>>(private val regex: Regex<A>) : DfaState<A, Unit> {
    override val result: Unit?
        get() = if (regex.containsEpsilon()) Unit else null

    override val possibleSteps: Map<A, DfaState<A, Unit>>
        get() = regex.first().associateWith { RegexDfaState(regex.derivative(it)) }
}
