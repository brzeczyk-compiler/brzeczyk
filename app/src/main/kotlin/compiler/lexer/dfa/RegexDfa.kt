package compiler.lexer.dfa

import compiler.lexer.regex.Regex
import compiler.lexer.regex.RegexFactory

class RegexDfa(private val regex: Regex<Char/*TODO: Replace with generic type*/>) : Dfa {
    override fun newWalk(): DfaWalk {
        return object : DfaWalk {
            var currentStateRegex = regex
            override fun isAccepted(): Boolean {
                return currentStateRegex.containsEpsilon()
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
