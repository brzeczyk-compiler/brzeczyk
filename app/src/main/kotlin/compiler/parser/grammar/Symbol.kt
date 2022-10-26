package compiler.parser.grammar

import compiler.lexer.lexer_grammar.TokenType

sealed class Symbol : Comparable<Symbol> {
    data class Terminal(val tokenType: TokenType) : Symbol() {
        override fun compareTo(other: Symbol): Int {
            TODO("Not yet implemented")
        }
    }

    data class NonTerminal(val nonTerminal: NonTerminalType) : Symbol() {
        override fun compareTo(other: Symbol): Int {
            TODO("Not yet implemented")
        }
    }
}
