package compiler.lexer.dfa

import compiler.lexer.regex.Regex

class RegexDfa(var regex: Regex) : Dfa {

    override fun newWalk(): DfaWalk {
        return object : DfaWalk {
            override fun isAccepted(): Boolean {
                // TODO
                return true
            }

            override fun isDead(): Boolean {
                // TODO
                return true
            }

            override fun step(a: Char) {
                // TODO
            }
        }
    }
}
