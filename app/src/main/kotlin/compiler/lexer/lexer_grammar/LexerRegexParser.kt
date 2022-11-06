package compiler.lexer.lexer_grammar

import compiler.common.regex.AbstractRegexParser
import compiler.common.regex.Regex
import compiler.common.regex.RegexFactory

object LexerRegexParser : AbstractRegexParser<Regex<Char>>() {
    val SPECIAL_SYMBOLS = mapOf(
        "l" to "aąbcćdeęfghijklłmnńoópqrsśtuvwxyzźż".toSet(),
        "u" to "AĄBCĆDEĘFGHIJKLŁMNŃOÓPQRSŚTUVWXYZŹŻ".toSet(),
        "d" to "0123456789".toSet(),
        "c" to "{}(),.<>:;?/+=-_!%^&*|~".toSet()
    )

    override fun performStar(child: Regex<Char>): Regex<Char> {
        return RegexFactory.createStar(child)
    }

    override fun performConcat(left: Regex<Char>, right: Regex<Char>): Regex<Char> {
        return RegexFactory.createConcat(left, right)
    }

    override fun performUnion(left: Regex<Char>, right: Regex<Char>): Regex<Char> {
        return RegexFactory.createUnion(left, right)
    }

    override fun getEmpty(): Regex<Char> {
        return RegexFactory.createEmpty()
    }

    override fun getAtomic(charSet: Set<Char>): Regex<Char> {
        return RegexFactory.createAtomic(charSet)
    }

    override fun getSpecialAtomic(string: String): Regex<Char> {
        return getAtomic(SPECIAL_SYMBOLS.getOrDefault(string, setOf(string[0])))
    }
}
