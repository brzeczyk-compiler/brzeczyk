package compiler.lexer

import compiler.common.dfa.AbstractDfa
import compiler.common.dfa.isAccepting
import compiler.lexer.input.Input

// Used to turn a sequence of characters into a sequence of tokens.
// The DFAs given are used to recognize the corresponding token categories.
// The priority of a DFA is given by its position in the list, with earlier positions having higher priority.
class Lexer<TCat>(val dfas: List<Pair<AbstractDfa<Char, Unit>, TCat>>) {
    // Thrown when none of the DFAs accepted the input.
    class FailedToMatchToken : Throwable()

    // Splits the given input into a sequence of tokens.
    // The input is read lazily as consecutive tokens are requested.
    // The matching is greedy - when multiple tokens match the input at a given location, the longest one is chosen.
    // Note that the complexity of this function is potentially quadratic if badly constructed DFAs are given.
    fun process(input: Input): Sequence<Token<TCat>> = sequence {
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
                        tokenEnd = location
                        tokenCategory = dfas[index].second
                        excess = 0
                        break
                    }
                }
            }

            if (tokenEnd == null || tokenCategory == null)
                throw FailedToMatchToken() // Throw an error when no token matches the remaining input.

            // Undo the reading of characters after the last matched token.
            val tokenContent = buffer.dropLast(excess).toString()
            input.rewind(excess)

            // Pass the matched token to the reader of this sequence and wait until the next token is requested.
            yield(Token(tokenCategory, tokenContent, tokenStart, tokenEnd))
        }
    }
}
