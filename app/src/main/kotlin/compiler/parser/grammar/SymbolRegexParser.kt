package compiler.parser.grammar

import compiler.common.regex.Regex
import compiler.common.regex.RegexFactory
import compiler.lexer.lexer_grammar.AbstractRegexParser
import compiler.lexer.lexer_grammar.TokenType
import java.lang.IllegalArgumentException

object SymbolRegexParser : AbstractRegexParser<Regex<Symbol>>() {
    override fun performStar(child: Regex<Symbol>): Regex<Symbol> {
        return RegexFactory.createStar(child)
    }

    override fun performConcat(left: Regex<Symbol>, right: Regex<Symbol>): Regex<Symbol> {
        return RegexFactory.createConcat(left, right)
    }

    override fun performUnion(left: Regex<Symbol>, right: Regex<Symbol>): Regex<Symbol> {
        return RegexFactory.createUnion(left, right)
    }

    override fun getEmpty(): Regex<Symbol> {
        return RegexFactory.createEmpty()
    }

    override fun getAtomic(charSet: Set<Char>): Regex<Symbol> {
        throw IllegalArgumentException()
    }

    override fun getSpecialAtomic(string: String): Regex<Symbol> {
        fun atomicOf(symbol: Symbol): Regex<Symbol> {
            return RegexFactory.createAtomic(setOf(symbol))
        }
        TokenType.values().find { it.name == string }?.let { return atomicOf(Symbol.Terminal(it)) }
        NonTerminalType.values().find { it.name == string }?.let { return atomicOf(Symbol.NonTerminal(it)) }
        throw IllegalArgumentException()
    }
}
