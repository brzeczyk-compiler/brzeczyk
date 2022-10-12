package compiler.lexer.dfa

interface DfaWalk {

    fun isAccepted(): Boolean

    fun isDead(): Boolean

    fun step(a: Char)
}
