package compiler.parser.grammar

import compiler.lexer.lexer_grammar.TokenType

sealed class Symbol : Comparable<Symbol> {
    data class Terminal(val tokenType: TokenType) : Symbol() {
        override fun compareTo(other: Symbol): Int {
            return if (other is Terminal) tokenType.compareTo(other.tokenType) else -1
        }
    }

    data class NonTerminal(val nonTerminal: NonTerminalType) : Symbol() {
        override fun compareTo(other: Symbol): Int {
            return if (other is NonTerminal) nonTerminal.compareTo(other.nonTerminal) else 1
        }
    }
}
