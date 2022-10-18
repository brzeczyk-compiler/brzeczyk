package compiler.common.dfa

interface DfaWalk<A, R> {
    fun getResult(): R

    fun isDead(): Boolean

    fun step(a: A)
}
