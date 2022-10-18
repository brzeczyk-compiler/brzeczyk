package compiler.common.dfa

interface Dfa<A, R> {
    fun newWalk(): DfaWalk<A, R>
}
