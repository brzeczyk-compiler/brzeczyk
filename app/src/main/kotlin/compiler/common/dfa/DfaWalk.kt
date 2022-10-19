package compiler.common.dfa

interface DfaWalk<A, R> {
    fun getAcceptingStateTypeOrNull(): R?

    fun isDead(): Boolean

    fun step(a: A)
}

fun <A, R> DfaWalk<A, R>.isAccepting(): Boolean {
    return getAcceptingStateTypeOrNull() != null
}
