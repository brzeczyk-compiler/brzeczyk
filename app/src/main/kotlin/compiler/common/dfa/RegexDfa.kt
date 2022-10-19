package compiler.common.dfa

import compiler.common.dfa.state_dfa.Dfa
import compiler.common.dfa.state_dfa.DfaState
import compiler.common.regex.Regex
import compiler.common.regex.RegexFactory

// TODO: replace Char with A and PlainDfaStateType with R
// it should be possible to use RegexDfa in AutomatonGrammar
class RegexDfa(private val regex: Regex<Char>) : Dfa<Char, Unit> {
    override val startState: DfaState<Char, Unit>
        get() = TODO("Not yet implemented")

    // TODO: remove if decided to create default implementation in TransparentDfa
    override fun newWalk(): DfaWalk<Char, Unit> {
        return object : DfaWalk<Char, Unit> {
            var currentStateRegex = regex
            override fun getAcceptingStateTypeOrNull(): Unit? {
                if (currentStateRegex.containsEpsilon())
                    return Unit
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
