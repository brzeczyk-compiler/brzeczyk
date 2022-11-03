package compiler.parser

import compiler.common.dfa.state_dfa.Dfa
import compiler.common.dfa.state_dfa.DfaState
import compiler.common.diagnostics.Diagnostics
import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.grammar.AutomatonGrammar
import compiler.parser.grammar.Grammar
import compiler.parser.grammar.Production

class Parser<S : Comparable<S>>(
    val automatonGrammar: AutomatonGrammar<S>,
    val diagnostics: Diagnostics
) {
    val analysisResults: GrammarAnalysis.Result<S>
    val parseActions: Map<Triple<Dfa<S, Production<S>>, DfaState<S, Production<S>>, S>, ParserAction<S>>
    class ParsingFailed : Throwable()
    class AmbiguousParseActions : Throwable()
    init {
        // TODO: Compute the parsing table
        analysisResults = GrammarAnalysis.Result(emptySet(), emptyMap(), emptyMap())
        parseActions = emptyMap()
    }

    constructor(grammar: Grammar<S>, diagnostics: Diagnostics) :
        this(AutomatonGrammar.createFromGrammar(grammar), diagnostics)

    fun process(input: Sequence<ParseTree<S>>): ParseTree<S> {
        TODO("Implement the parser")
    }
}
