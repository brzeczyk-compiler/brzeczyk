package compiler.common.dfa

import compiler.common.dfa.state_dfa.Dfa
import compiler.common.dfa.state_dfa.DfaState
import compiler.common.regex.Regex

// it should be possible to use RegexDfa in AutomatonGrammar
class RegexDfa<A : Comparable<A>>(private val regex: Regex<A>) : Dfa<A, Unit> {
    override val startState: DfaState<A, Unit>
        get() = RegexDfaState(regex)
}
