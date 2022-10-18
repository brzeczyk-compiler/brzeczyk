package compiler.parser.grammar
import compiler.common.dfa.Dfa

class AutomatonGrammar<S : Comparable<S>> {
    fun getState(): S {
        return TODO()
    }
    fun getProductions(): Map<S, Dfa<S, Production<S>>> {
        return TODO()
    }
}
