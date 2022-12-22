package compiler.lexer

import compiler.dfa.AbstractDfa
import compiler.dfa.isAccepting
import compiler.diagnostics.Diagnostic
import compiler.diagnostics.Diagnostics
import compiler.input.Input
import compiler.input.Location
import compiler.input.LocationRange

// Used to turn a sequence of characters into a sequence of tokens.
// The DFAs given are used to recognize the corresponding token categories.
// The priority of a DFA is given by its position in the list, with earlier positions having higher priority.
class Lexer<TCat>(
    val dfas: List<Pair<AbstractDfa<Char, Unit>, TCat>>,
    val diagnostics: Diagnostics,
    val contextErrorLength: Int = 3
) {

    // Splits the given input into a sequence of tokens.
    // The input is read lazily as consecutive tokens are requested.
    // The matching is greedy - when multiple tokens match the input at a given location, the longest one is chosen.
    // Note that the complexity of this function is potentially quadratic if badly constructed DFAs are given.
    fun process(input: Input): Sequence<Token<TCat>> = sequence<Token<TCat>> {

        var isErrorSegment = false
        var errorSegmentStart: Location? = null
        val errorSegment = StringBuilder()
        val context = ArrayDeque<String>(contextErrorLength)

        fun addToContext(content: String) {
            context.addLast(content)
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
                            diagnostics.report(Diagnostic.LexerError(errorSegmentStart!!, tokenStart, context.toList(), errorSegment.toString()))
                            addToContext(errorSegment.toString())
                            errorSegment.clear()
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

                if (!isErrorSegment) {
                    isErrorSegment = true
                    errorSegmentStart = tokenStart
                }

                errorSegment.append(input.next())

                if (!input.hasNext()) {
                    diagnostics.report(Diagnostic.LexerError(errorSegmentStart!!, null, context, errorSegment.toString()))
                }
            } else {
                val tokenContent = buffer.dropLast(excess).toString()
                addToContext(tokenContent)
                yield(Token(tokenCategory, tokenContent, LocationRange(tokenStart, tokenEnd)))
            }
        }
    }
}
