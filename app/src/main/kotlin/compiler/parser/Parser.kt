package compiler.parser

import compiler.common.dfa.state_dfa.Dfa
import compiler.common.dfa.state_dfa.DfaState
import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.lexer.Location
import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.grammar.AutomatonGrammar
import compiler.parser.grammar.Grammar
import compiler.parser.grammar.Production

class Parser<S : Comparable<S>>(
    private val automatonGrammar: AutomatonGrammar<S>,
    private val diagnostics: Diagnostics
) {
    private val analysisResults: GrammarAnalysis.Result<S>
    private val parseActions: Map<Triple<Dfa<S, Production<S>>, DfaState<S, Production<S>>, S?>, ParserAction<S>>

    init {
        // TODO: Compute the parsing table
        analysisResults = GrammarAnalysis.Result(emptySet(), emptyMap(), emptyMap())
        parseActions = emptyMap()
    }

    constructor(grammar: Grammar<S>, diagnostics: Diagnostics) :
        this(AutomatonGrammar.createFromGrammar(grammar), diagnostics)

    // Thrown when the parser is unable to recover from an error.
    class ParsingFailed : Throwable()

    // Parses the input token sequence and returns a parse tree as a result.
    // Each input token should be given as a leaf ParseTree with its corresponding symbol.
    // Note that this function does not assume the actual type of these leaves,
    // and so it may as well work with full subtrees corresponding to non-terminals.
    fun process(input: Iterator<ParseTree<S>>): ParseTree<S> {
        // Contains the data that would normally be stored in local variables of a recursive function to parse a given symbol.
        // We use an explicit call stack in order to be able to examine and manipulate it when recovering from errors.
        class Call(
            val symbol: S,
            val dfa: Dfa<S, Production<S>> = automatonGrammar.productions[symbol]!!,
            var state: DfaState<S, Production<S>> = dfa.startState,
            val parsedSubtrees: MutableList<ParseTree<S>> = mutableListOf()
        )

        val callStack = mutableListOf(Call(automatonGrammar.start))
        var callResult: ParseTree<S>? = null
        var lookahead: ParseTree<S>? = null
        var lookaheadStart = Location(1, 1)
        var lookaheadEnd = Location(1, 1)

        fun shift() {
            if (input.hasNext()) {
                lookahead = input.next()
                lookaheadStart = lookahead!!.start
                lookaheadEnd = lookahead!!.end
            } else
                lookahead = null
        }

        shift()

        while (callStack.isNotEmpty()) {
            callStack.last().apply {
                // Store the result of a Reduce action.
                // It would normally be the value returned by a recursive function call.
                callResult?.let { parsedSubtrees.add(it) }
                callResult = null

                // Consult the parsing table in order to decide what to do.
                // A null lookahead symbol corresponds to the end of input.
                val action = parseActions[Triple(dfa, state, lookahead?.symbol)]

                when (action) {
                    is ParserAction.Shift -> {
                        // We assume that
                        // (1) the parsing table never tells to do Shift at the end of input, and that
                        // (2) the parsing table only tells to do Shift when a DFA step can be made with the lookahead symbol.
                        state = state.possibleSteps[lookahead!!.symbol]!!
                        parsedSubtrees.add(lookahead!!)
                        shift()
                    }

                    is ParserAction.Call -> {
                        // We assume that the parsing table only tells to do Call with a correct symbol.
                        state = state.possibleSteps[action.symbol]!!
                        callStack.add(Call(action.symbol))
                    }

                    is ParserAction.Reduce -> {
                        val start: Location
                        val end: Location

                        if (parsedSubtrees.isNotEmpty()) {
                            start = parsedSubtrees.first().start
                            end = parsedSubtrees.last().end
                        } else {
                            // An empty non-terminal symbol has (somewhat arbitrarily) the location of the next input symbol.
                            start = lookaheadStart
                            end = lookaheadEnd
                        }

                        callResult = ParseTree.Branch(start, end, symbol, parsedSubtrees, action.production)
                        callStack.removeLast()
                    }

                    null -> {
                        // Report a parsing error when the parsing table does not tell what to do.
                        val expectedSymbols = parseActions.keys.filter { it.first == dfa && it.second == state }.mapNotNull { it.third?.toString() }
                        diagnostics.report(Diagnostic.ParserError(lookahead?.symbol?.toString(), lookaheadStart, lookaheadEnd, expectedSymbols))

                        // Attempt to resume parsing by skipping input symbols until finding
                        // one compatible with some state in the call stack.
                        // The topmost compatible state is used, and everything above is discarded.
                        panic@ while (true) {
                            for ((index, call) in callStack.withIndex().reversed()) {
                                if (Triple(call.dfa, call.state, lookahead?.symbol) in parseActions) {
                                    while (index != callStack.lastIndex)
                                        callStack.removeLast()
                                    break@panic
                                }
                            }

                            if (lookahead == null)
                                throw ParsingFailed()

                            shift()
                        }
                    }
                }
            }
        }

        // The call stack can only be emptied with a Reduce action,
        // and so the final callResult must be a node corresponding to a production from the start symbol.
        return callResult!!
    }

    fun process(input: Sequence<ParseTree<S>>): ParseTree<S> = process(input.iterator())
}
