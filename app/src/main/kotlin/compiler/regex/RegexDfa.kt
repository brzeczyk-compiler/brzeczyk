package compiler.regex

import compiler.dfa.Dfa
import compiler.dfa.DfaState

// it should be possible to use RegexDfa in AutomatonGrammar
class RegexDfa<A : Comparable<A>>(private val regex: Regex<A>) : Dfa<A, Unit> {
    override val startState: DfaState<A, Unit>
        get() = RegexDfaState(regex)
}
