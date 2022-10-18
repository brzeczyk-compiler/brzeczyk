package compiler.lexer.dfa

interface Dfa<A, R> {
    fun newWalk(): DfaWalk<A, R>
}
