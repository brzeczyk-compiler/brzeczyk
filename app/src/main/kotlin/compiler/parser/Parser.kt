package compiler.parser

import compiler.common.dfa.state_dfa.Dfa
import compiler.common.dfa.state_dfa.DfaState
import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.parser.analysis.GrammarAnalysis
import compiler.parser.grammar.AutomatonGrammar
import compiler.parser.grammar.Grammar
import compiler.parser.grammar.Production

class Parser<S : Comparable<S>>(
    private val automatonGrammar: AutomatonGrammar<S>,
    private val diagnostics: Diagnostics
) {
    private val parseActions: Map<Triple<Dfa<S, Production<S>>, DfaState<S, Production<S>>, S?>, ParserAction<S>>

    class AmbiguousParseActions : Throwable()

    init {
        val grammarAnalysis = GrammarAnalysis<S>()

        val nullable = grammarAnalysis.computeNullable(automatonGrammar)
        val first = grammarAnalysis.computeFirst(automatonGrammar, nullable)
        val follow = grammarAnalysis.computeFollow(automatonGrammar, nullable, first)
        val firstPlus = grammarAnalysis.computeFirstPlus(nullable, first, follow)

        val grammarSymbols = automatonGrammar.getSymbols()

        parseActions = mutableMapOf()

        for (dfa in automatonGrammar.productions.values) {
            val acceptingStates = dfa.getAcceptingStates()

            for (state in dfa.getStates()) {

                for (lookAheadSymbol in grammarSymbols.union(setOf(null))) { // null represents the end of the input
                    fun alreadySet(): Boolean = parseActions.containsKey(Triple(dfa, state, lookAheadSymbol))

                    // Try to match with a reduce action. The condition is:
                    // (the state is accepting) AND (there exists a symbol A such that the look-ahead symbol is in FOLLOW(A))
                    if (acceptingStates.contains(state)) {
                        for (symbol in grammarSymbols) {
                            if (follow[symbol]!!.contains(lookAheadSymbol)) {
                                if (alreadySet())
                                    throw AmbiguousParseActions()

                                parseActions[Triple(dfa, state, lookAheadSymbol)] =
                                    ParserAction.Reduce(state.result!!)
                            }
                        }
                    }

                    // Try to match with a shift action. The condition is:
                    // (there exists an edge from the state labelled with the look-ahead symbol)
                    if (state.possibleSteps.containsKey(lookAheadSymbol)) {
                        if (alreadySet())
                            throw AmbiguousParseActions()

                        parseActions[Triple(dfa, state, lookAheadSymbol)] = ParserAction.Shift()
                    }

                    // Try to match with a call action. The condition is:
                    // (there exists an edge from the state labelled with such A that the look-ahead symbol is in FIRST+(A))
                    for ((otherSymbol, _) in state.possibleSteps) {
                        if (
                            (lookAheadSymbol != null && firstPlus[otherSymbol]!!.contains(lookAheadSymbol)) ||
                            (lookAheadSymbol == null || nullable.contains(otherSymbol)) // null look-ahead symbol requires special treatment
                        ) {
                            if (alreadySet())
                                throw AmbiguousParseActions()

                            parseActions[Triple(dfa, state, lookAheadSymbol)] = ParserAction.Call(otherSymbol)
                        }
                    }
                }
            }
        }
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

        fun <T> Iterator<T>.nextOrNull() = if (hasNext()) next() else null

        var lookahead = input.nextOrNull()
        val callStack = mutableListOf(Call(automatonGrammar.start))
        var callResult: ParseTree<S>? = null

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
                        lookahead = input.nextOrNull()
                    }

                    is ParserAction.Call -> {
                        // We assume that the parsing table only tells to do Call with a correct symbol.
                        state = state.possibleSteps[action.symbol]!!
                        callStack.add(Call(action.symbol))
                    }

                    is ParserAction.Reduce -> {
                        // A parse tree node corresponding to an empty production does not have a location.
                        // A parse tree node corresponding to a non-empty production has a location
                        // which spans the locations of the node's children.
                        val start = parsedSubtrees.firstNotNullOfOrNull { it.start }
                        val end = parsedSubtrees.reversed().firstNotNullOfOrNull { it.end }
                        callResult = ParseTree.Branch(start, end, symbol, parsedSubtrees, action.production)
                        callStack.removeLast()
                    }

                    null -> {
                        // Report a parsing error when the parsing table does not tell what to do.
                        val expectedSymbols = parseActions.keys.filter { it.first == dfa && it.second == state }.mapNotNull { it.third }
                        diagnostics.report(Diagnostic.ParserError(lookahead?.symbol, lookahead?.start, lookahead?.end, expectedSymbols))

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

                            lookahead = input.nextOrNull()
                        }
                    }
                }
            }
        }

        // The call stack can only be emptied with a Reduce action,
        // and so the final callResult must be a node corresponding to a production from the start symbol.
        return callResult!!
    }
}
