package compiler.lexer.dfa

import compiler.lexer.regex.Regex
import compiler.lexer.regex.RegexFactory

class RegexDfa(private val regex: Regex) : Dfa {
    override fun newWalk(): DfaWalk {
        return object : DfaWalk {
            var currentStateRegex = regex
            override fun isAccepted(): Boolean {
                return currentStateRegex.containsEpsilon()
            }

            override fun isDead(): Boolean {
                return currentStateRegex == RegexFactory.createEmpty()
            }

            override fun step(a: Char) {
                currentStateRegex = currentStateRegex.derivative(a)
            }
        }
    }
}
