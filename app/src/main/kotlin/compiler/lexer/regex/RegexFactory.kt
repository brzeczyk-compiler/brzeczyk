package compiler.lexer.regex

class RegexFactory {

    fun createEmpty(): Regex {
        return Regex.Empty()
    }

    fun createEpsilon(): Regex {
        return Regex.Epsilon()
    }

    fun createAtomic(atoms: Set<Char>): Regex {
        // TODO: normalize
        return Regex.Atomic(atoms)
    }

    fun createStar(child: Regex): Regex {
        // TODO: normalize
        return Regex.Star(child)
    }

    fun createUnion(left: Regex, right: Regex): Regex {
        // TODO: normalize
        return Regex.Union(left, right)
    }

    fun createConcat(left: Regex, right: Regex): Regex {
        // TODO: normalize
        return Regex.Concat(left, right)
    }
}
