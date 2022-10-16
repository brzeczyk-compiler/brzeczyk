package compiler.lexer.lexer_grammar

import compiler.lexer.regex.Regex
import compiler.lexer.regex.RegexFactory

object RegexParser : UniversalRegexParser<Regex>() {

    override fun performStar(child: Regex): Regex {
        return RegexFactory.createStar(child)
    }

    override fun performConcat(left: Regex, right: Regex): Regex {
        return RegexFactory.createConcat(left, right)
    }

    override fun performUnion(left: Regex, right: Regex): Regex {
        return RegexFactory.createUnion(left, right)
    }

    override fun getEmpty(): Regex {
        return RegexFactory.createEmpty()
    }

    override fun getAtomic(charSet: Set<Char>): Regex {
        return RegexFactory.createAtomic(charSet)
    }
}
