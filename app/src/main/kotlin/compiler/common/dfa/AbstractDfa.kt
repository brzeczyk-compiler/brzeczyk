package compiler.common.dfa

interface AbstractDfa<A, R> {
    fun newWalk(): DfaWalk<A, R>
}
