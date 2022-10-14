package compiler.lexer.lexer_grammar

import java.util.Stack

abstract class UniversalRegexParser<T> {
    companion object {
        val OPERATOR_PRIORITY = mapOf(
            '(' to 0,
            '|' to 1,
            '?' to 2,
            '*' to 3
        )
        val SPECIAL_SYMBOLS = mapOf(
            "\\l" to "aąbcćdeęfghijklłmnńoópqrsśtuvwxyzźż".toSet(),
            "\\u" to "AĄBCĆDEĘFGHIJKLŁMNŃOÓPQRSŚTUVWXYZŹŻ".toSet(),
            "\\d" to "0123456789".toSet(),
            "\\c" to "{}(),.<>:;?/+=-_".toSet()
        )
    }

    protected abstract fun starBehaviour(a: T): T
    protected abstract fun concatBehaviour(a: T, b: T): T
    protected abstract fun unionBehaviour(a: T, b: T): T
    protected abstract fun getEmpty(): T
    protected abstract fun getAtomic(s: Set<Char>): T

    private class SymbolSeq(private val text: String) : Iterable<String> {
        override fun iterator(): Iterator<String> {
            return object : Iterator<String> {
                var position = 0

                override fun next(): String {
                    return when (text[position]) {
                        '[' -> {
                            val begin = position
                            position = text.indexOf(']', begin) + 1
                            text.substring(begin, position)
                        }

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

    private fun parseSymbol(a: String): T {
        return when (a[0]) {
            '[' -> {
                var out = getEmpty()
                for (symbol in SymbolSeq(a.substring(1, a.length - 1))) {
                    out = unionBehaviour(out, parseSymbol(symbol))
                }
                out
            }

            '\\' -> when (a) {
                in SPECIAL_SYMBOLS.keys -> getAtomic(SPECIAL_SYMBOLS[a]!!)
                else -> getAtomic(setOf(a[1]))
            }

            else -> getAtomic(setOf(a[0]))
        }
    }

    private fun generateRPN(string: String): String {
        if (string.isEmpty()) return "[]" // empty set
        var out = ""
        val stack = Stack<Char>()
        fun putOperatorOnStack(x: Char) {
            while (!stack.isEmpty() && (OPERATOR_PRIORITY[stack.peek()]!! >= OPERATOR_PRIORITY[x]!!)) out += stack.pop()
            stack.push(x)
        }

        var didTokenEndInPrevStep = false
        for (next in SymbolSeq(string)) {
            val c = next[0]
            val isNewTokenStarting = !(c in listOf('|', '*', ')'))
            if (didTokenEndInPrevStep && isNewTokenStarting) putOperatorOnStack('?') // concat
            didTokenEndInPrevStep = false
            when (c) {
                '(' -> stack.push(c)
                ')' -> {
                    while (stack.peek() != '(') out += stack.pop()
                    stack.pop()
                    didTokenEndInPrevStep = true
                }

                in OPERATOR_PRIORITY.keys -> {
                    putOperatorOnStack(c)
                    if (c == '*') didTokenEndInPrevStep = true
                }

                else -> {
                    out += next
                    didTokenEndInPrevStep = true
                }
            }
        }
        while (!stack.isEmpty()) out += stack.pop()
        return out
    }

    fun parseStringToRegex(string: String): T {
        val rpn = generateRPN(string)
        val stack = Stack<T>()
        for (next in SymbolSeq(rpn)) {
            when (next[0]) {
                '*' -> stack.push(starBehaviour(stack.pop()))
                '|' -> stack.push(unionBehaviour(stack.pop(), stack.pop()))
                '?' -> stack.push(concatBehaviour(stack.pop(), stack.pop()))
                else -> stack.push(parseSymbol(next))
            }
        }
        return stack.peek()
    }
} //
