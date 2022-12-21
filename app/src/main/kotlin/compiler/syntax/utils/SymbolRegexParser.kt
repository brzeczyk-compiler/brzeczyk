package compiler.syntax.utils

import compiler.regex.Regex
import compiler.regex.RegexFactory
import compiler.syntax.NonTerminalType
import compiler.syntax.Symbol
import compiler.syntax.TokenType
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

    fun getSymbolFromString(string: String): Symbol {
        return when (string[0]) {
            'n' -> Symbol.NonTerminal(NonTerminalType.valueOf(string.substring(1)))
            't' -> Symbol.Terminal(TokenType.valueOf(string.substring(1)))
            else -> throw IllegalArgumentException()
        }
    }

    override fun getSpecialAtomic(string: String): Regex<Symbol> {
        return RegexFactory.createAtomic(setOf(getSymbolFromString(string)))
    }
}
