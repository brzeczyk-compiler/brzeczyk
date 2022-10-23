package compiler.parser.grammar
import compiler.common.dfa.state_dfa.Dfa

data class AutomatonGrammar<S : Comparable<S>>(val startState: S, val productions: Map<S, Dfa<S, Production<S>>>)
