package compiler.lexer.lexer_grammar

import compiler.lexer.regex.Regex
import compiler.lexer.regex.RegexFactory

class RegexParser : UniversalRegexParser<Regex>() {

    override fun starBehaviour(a: Regex): Regex {
        return RegexFactory.createStar(a)
    }

    override fun concatBehaviour(a: Regex, b: Regex): Regex {
        return RegexFactory.createConcat(a, b)
    }

    override fun unionBehaviour(a: Regex, b: Regex): Regex {
        return RegexFactory.createUnion(a, b)
    }

    override fun getEmpty(): Regex {
        return RegexFactory.createEmpty()
    }

    override fun getAtomic(s: Set<Char>): Regex {
        return RegexFactory.createAtomic(s)
    }
}
