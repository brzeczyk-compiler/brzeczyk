package compiler.regex

import compiler.dfa.DfaState

data class RegexDfaState<A : Comparable<A>>(private val regex: Regex<A>) : DfaState<A, Unit> {
    override val result: Unit?
        get() = if (regex.containsEpsilon()) Unit else null

    override val possibleSteps: Map<A, DfaState<A, Unit>>
        get() = regex.first().associateWith { RegexDfaState(regex.derivative(it)) }
}
