package compiler.dfa

interface AbstractDfa<A, R> {
    fun newWalk(): DfaWalk<A, R>
}
