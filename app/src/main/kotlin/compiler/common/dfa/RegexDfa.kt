package compiler.common.dfa

import compiler.common.dfa.state_dfa.DfaState
import compiler.common.dfa.state_dfa.SingleAcceptingState
import compiler.common.dfa.state_dfa.TransparentDfa
import compiler.common.regex.Regex
import compiler.common.regex.RegexFactory

// TODO: replace Char with A and PlainDfaStateType with R
// it should be possible to use RegexDfa in AutomatonGrammar
class RegexDfa(private val regex: Regex<Char>) : TransparentDfa<Char, SingleAcceptingState> {
    override val startState: DfaState<Char, SingleAcceptingState>
        get() = TODO("Not yet implemented")

    // TODO: remove if decided to create default implementation in TransparentDfa
    override fun newWalk(): DfaWalk<Char, SingleAcceptingState> {
        return object : DfaWalk<Char, SingleAcceptingState> {
            var currentStateRegex = regex
            override fun getAcceptingStateTypeOrNull(): SingleAcceptingState? {
                if (currentStateRegex.containsEpsilon())
                    return SingleAcceptingState.ACCEPTING
                return null
            }

            override fun isDead(): Boolean {
                return currentStateRegex == RegexFactory.createEmpty<Char>()
            }

            override fun step(a: Char) {
                currentStateRegex = currentStateRegex.derivative(a)
            }
        }
    }
}
