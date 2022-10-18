package compiler.common.dfa

interface DfaWalk<A, R> {
    fun getAcceptingStateTypeOrNull(): R?

    fun isAccepting(): Boolean {
        return getAcceptingStateTypeOrNull() != null
    }

    fun isDead(): Boolean

    fun step(a: A)
}
