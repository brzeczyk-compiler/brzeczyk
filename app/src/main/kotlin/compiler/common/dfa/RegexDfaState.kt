package compiler.common.dfa

import compiler.common.dfa.state_dfa.DfaState
import compiler.common.regex.Regex

class RegexDfaState<A : Comparable<A>>(private val regex: Regex<A>) : DfaState<A, Unit> {
    override val result: Unit?
        get() = if (regex.containsEpsilon()) Unit else null

    override val possibleSteps: Map<A, DfaState<A, Unit>>
        get() = regex.first().associateWith { RegexDfaState(regex.derivative(it)) }

    override fun equals(other: Any?): Boolean {
        if (other !is RegexDfaState<*>)
            return false
        return regex == other.regex
    }

    override fun hashCode(): Int {
        return regex.hashCode()
    }

    override fun toString(): String {
        return regex.toString()
    }
}
