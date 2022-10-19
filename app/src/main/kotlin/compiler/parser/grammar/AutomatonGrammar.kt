package compiler.parser.grammar
import compiler.common.dfa.state_dfa.TransparentDfa

class AutomatonGrammar<S : Comparable<S>> {
    fun getStartState(): S {
        return TODO()
    }
    fun getProductions(): Map<S, TransparentDfa<S, Production<S>>> {
        return TODO()
    }
}
