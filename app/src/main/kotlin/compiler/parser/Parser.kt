package compiler.parser

import compiler.common.dfa.state_dfa.DfaState
import compiler.common.diagnostics.Diagnostics
import compiler.parser.grammar.AutomatonGrammar
import compiler.parser.grammar.Grammar
import compiler.parser.grammar.Production

class Parser<S : Comparable<S>>(
    val automatonGrammar: AutomatonGrammar<S>,
    val nullable: Set<S>,
    val first: Map<S, Set<S>>,
    val follow: Map<S, Set<S>>,
    val diagnostics: Diagnostics
) {
    val parseActions: Map<Pair<DfaState<S, Production<S>>, S>, Action<S>>

    init {
        // TODO: Compute the parsing table
        parseActions = emptyMap()
    }

    companion object {
        fun <S : Comparable<S>> create(grammar: Grammar<S>): Parser<S> {
            TODO("Create parser from grammar")
        }
    }

    fun process(input: Sequence<ParseTree<S>>): ParseTree<S> {
        TODO("Implement the parser")
    }
}
