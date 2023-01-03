package compiler.syntax.utils

import java.util.Stack

abstract class AbstractRegexParser<T> {
    companion object {
        val OPERATOR_PRIORITY = mapOf(
            '(' to 0,
            '|' to 1,
            '¿' to 2,
            '*' to 3,
            '?' to 3
        )
        val SPECIAL_SYMBOLS = "(){}[]|¿*?\\".toSet() // have to be escaped with \ in regex
    }

    protected abstract fun performStar(child: T): T
    protected abstract fun performConcat(left: T, right: T): T
    protected abstract fun performUnion(left: T, right: T): T
    protected abstract fun getEmpty(): T
    protected abstract fun getAtomic(charSet: Set<Char>): T
    protected abstract fun getSpecialAtomic(string: String): T
    private fun performOptional(child: T): T {
        return performUnion(child, performStar(getEmpty()))
    }

    private class SymbolSeq(private val text: String) : Iterable<String> {
        override fun iterator(): Iterator<String> {
            return object : Iterator<String> {
                var position = 0

                override fun next(): String {
                    fun readToBracket(closingBracket: Char): String {
                        val begin = position
                        position = text.indexOf(closingBracket, begin) + 1
                        if (position == 0) throw IllegalArgumentException("The bracket at position $begin has no corresponding closing bracket")
                        return text.substring(begin, position)
                    }

                    return when (text[position]) {
                        '[' -> readToBracket(']')

                        '{' -> readToBracket('}')

                        '\\' -> {
                            position += 2
                            text.substring(position - 2, position)
                        }

                        else -> text[position++].toString()
                    }
                }

                override fun hasNext(): Boolean {
                    return position < text.length
                }
            }
        }
    }

    private fun parseSymbol(symbol: String): T {
        return when (symbol[0]) {
            '[' -> {
                var result = getEmpty()
                for (insideSymbol in SymbolSeq(symbol.substring(1, symbol.length - 1))) {
                    result = performUnion(result, parseSymbol(insideSymbol))
                }
                result
            }

            '{' -> getSpecialAtomic(symbol.substring(1, symbol.length - 1))

            '\\' -> when (val specialChar = symbol[1]) {
                in SPECIAL_SYMBOLS -> getAtomic(setOf(specialChar))
                else -> getSpecialAtomic(specialChar.toString())
            }

            else -> getAtomic(setOf(symbol[0]))
        }
    }

    private fun generateReversePolishNotation(string: String): String {
        if (string.isEmpty()) return "[]" // empty set
        var result = ""
        val stack = Stack<Char>()
        fun putOperatorOnStack(x: Char) {
            while (!stack.isEmpty() && (OPERATOR_PRIORITY[stack.peek()]!! >= OPERATOR_PRIORITY[x]!!)) {
                result += stack.pop()
            }
            stack.push(x)
        }

        var didTokenEndInPrevStep = false
        for (next in SymbolSeq(string)) {
            val c = next[0]
            val isNewTokenStarting = c !in listOf('|', '*', ')', '?')
            if (didTokenEndInPrevStep && isNewTokenStarting) putOperatorOnStack('¿') // concat
            didTokenEndInPrevStep = false
            when (c) {
                '(' -> stack.push(c)
                ')' -> {
                    while (!stack.isEmpty() && stack.peek() != '(') result += stack.pop()
                    if (stack.isEmpty()) throw IllegalArgumentException("The string contains a closing parenthesis that does not have a corresponding opening parenthesis")
                    stack.pop()
                    didTokenEndInPrevStep = true
                }

                in OPERATOR_PRIORITY.keys -> {
                    putOperatorOnStack(c)
                    if (c in setOf('*', '?')) didTokenEndInPrevStep = true
                }

                else -> {
                    result += next
                    didTokenEndInPrevStep = true
                }
            }
        }
        while (!stack.isEmpty()) result += stack.pop()
        return result
    }

    fun parseStringToRegex(string: String): T {
        val rpn = generateReversePolishNotation(string)
        val stack = Stack<T>()
        for (next in SymbolSeq(rpn)) {
            when (next[0]) {
                '*' -> stack.push(performStar(stack.pop()))
                '?' -> stack.push(performOptional(stack.pop()))
                '|' -> {
                    val right = stack.pop()
                    val left = stack.pop()
                    stack.push(performUnion(left, right))
                }

                '¿' -> {
                    val right = stack.pop()
                    val left = stack.pop()
                    stack.push(performConcat(left, right))
                }

                else -> stack.push(parseSymbol(next))
            }
        }
        return stack.peek()
    }
}
