package compiler.lexer

import compiler.common.dfa.AbstractDfa
import compiler.common.dfa.isAccepting
import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.lexer.input.Input

// Used to turn a sequence of characters into a sequence of tokens.
// The DFAs given are used to recognize the corresponding token categories.
// The priority of a DFA is given by its position in the list, with earlier positions having higher priority.
class Lexer<TCat>(val dfas: List<Pair<AbstractDfa<Char, Unit>, TCat>>, val diagnostics: Diagnostics, val contextErrorLength: Int) {

    // Splits the given input into a sequence of tokens.
    // The input is read lazily as consecutive tokens are requested.
    // The matching is greedy - when multiple tokens match the input at a given location, the longest one is chosen.
    // Note that the complexity of this function is potentially quadratic if badly constructed DFAs are given.
    fun process(input: Input): Sequence<Token<TCat>> = sequence {

        var isErrorSegment = false
        var errorSegmentStart: Location? = null
        val errorSegment = StringBuilder()
        val context = ArrayDeque<String>(contextErrorLength)

        fun addTokenToContext(context: ArrayDeque<String>, tokenContent: String){
            context.addLast(tokenContent)
            if (context.size > contextErrorLength) context.removeFirst()
        }

        while (input.hasNext()) {
            input.flush() // Tell the input source that previous characters can be discarded.

            val buffer = StringBuilder() // The content of a token currently attempted to be matched.
            val tokenStart = input.getLocation() // The start location of a token currently attempted to be matched.
            var tokenEnd: Location? = null // The end location of the last matched token, if any.
            var tokenCategory: TCat? = null // The category of the last matched token, if any.
            var excess = 0 // The number of characters added to the buffer after the last matched token, if any.

            val walks = dfas.map { it.first.newWalk() }

            while (input.hasNext() && walks.any { !it.isDead() }) { // Try to match as long a token as possible.
                val location = input.getLocation()
                val char = input.next()
                buffer.append(char)
                excess++

                walks.forEach { it.step(char) }

                for ((index, walk) in walks.withIndex()) {
                    if (walk.isAccepting()) { // Match a token with category corresponding to the first accepting DFA.
                        if (isErrorSegment) {
                            isErrorSegment = false
                            diagnostics.report(Diagnostic.LexerError(errorSegmentStart!!, tokenStart, context, errorSegment.toString()))
                        }
                        tokenEnd = location
                        tokenCategory = dfas[index].second
                        excess = 0
                        break
                    }
                }
            }

            input.rewind(excess)

            if (tokenEnd == null || tokenCategory == null) {
                errorSegment.append(buffer.first())
                if (!isErrorSegment) {
                    isErrorSegment = true
                    errorSegmentStart = tokenStart
                    context.clear()
                }
                if (!input.hasNext()) {
                    diagnostics.report(Diagnostic.LexerError(errorSegmentStart!!, null, context, errorSegment.toString()))
                }
            } else {
                val tokenContent = buffer.dropLast(excess).toString()
                addTokenToContext(context, tokenContent)
                yield(Token(tokenCategory, tokenContent, tokenStart, tokenEnd))
            }
        }
    }
}
