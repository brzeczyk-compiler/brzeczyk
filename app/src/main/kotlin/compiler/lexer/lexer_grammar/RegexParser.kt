package compiler.lexer.lexer_grammar

import compiler.common.regex.Regex
import compiler.common.regex.RegexFactory

object RegexParser : UniversalRegexParser<Regex<Char>>() {

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
}
