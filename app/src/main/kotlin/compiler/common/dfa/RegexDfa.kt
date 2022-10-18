package compiler.common.dfa

import compiler.common.dfa.state_dfa.DfaState
import compiler.common.dfa.state_dfa.TransparentDfa
import compiler.common.dfa.state_dfa.PlainDfaStateType
import compiler.common.regex.Regex
import compiler.common.regex.RegexFactory

// TODO: replace Char with A and PlainDfaStateType with R
// it should be possible to use RegexDfa in AutomatonGrammar
class RegexDfa(private val regex: Regex<Char>) : TransparentDfa<Char, PlainDfaStateType> {
    override val startState: DfaState<Char, PlainDfaStateType>
        get() = TODO("Not yet implemented")

    override fun newWalk(): DfaWalk<Char, PlainDfaStateType> {
        return object : DfaWalk<Char, PlainDfaStateType> {
            var currentStateRegex = regex
            override fun getResult(): PlainDfaStateType {
                return if (currentStateRegex.containsEpsilon()) PlainDfaStateType.ACCEPTING
                else PlainDfaStateType.NON_ACCEPTING
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
