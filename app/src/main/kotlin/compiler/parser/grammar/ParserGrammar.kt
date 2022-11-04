package compiler.parser.grammar

object ParserGrammar {

    private fun getProduction(lhs: String, regex: String): Production<Symbol> {
        return Production(
            SymbolRegexParser.getSymbolFromString("n$lhs"),
            SymbolRegexParser.parseStringToRegex(regex)
        )
    }

    fun getGrammar(): Grammar<Symbol> {
        val productions = listOf<Production<Symbol>>(
            // TODO: fill this list
        )
        return Grammar(SymbolRegexParser.getSymbolFromString("nPROGRAM"), productions)
    }
}
