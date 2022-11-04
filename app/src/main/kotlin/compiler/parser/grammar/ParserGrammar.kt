package compiler.parser.grammar

object ParserGrammar {

    private fun getProduction(lhs: String, regex: String): Production<Symbol> {
        return Production(
            SymbolRegexParser.getSymbolFromString("n$lhs"),
            SymbolRegexParser.parseStringToRegex(regex)
        )
    }

    fun getGrammar(): Grammar<Symbol> {
        val productions = listOf(
            getProduction("MANY_STATEMENTS", "(\\{nSTATEMENT}|\\{tNEWLINE})*"),

            getProduction("MAYBE_BLOCK", "\\{tNEWLINE}*\\{nNON_BRACE_STATEMENT}"),
            getProduction("MAYBE_BLOCK", "\\{tLEFT_BRACE}\\{nMANY_STATEMENTS}\\{tRIGHT_BRACE}"),

            getProduction("STATEMENT", "\\{nNON_BRACE_STATEMENT}"),
            getProduction("STATEMENT", "\\{tLEFT_BRACE}\\{nMANY_STATEMENTS}\\{tRIGHT_BRACE}"),

            getProduction("NON_BRACE_STATEMENT", "\\{nATOMIC_STATEMENT}(\\{tNEWLINE}|\\{tSEMICOLON})"),
            getProduction("NON_BRACE_STATEMENT", "\\{tIF}\\{tLEFT_PAREN}\\{nEXPR}\\{tRIGHT_PAREN}\\{nMAYBE_BLOCK}(\\{tELSE_IF}\\{tLEFT_PAREN}\\{nEXPR}\\{tRIGHT_PAREN}\\{nMAYBE_BLOCK})*(\\{tELSE}\\{nMAYBE_BLOCK})?"),
            getProduction("NON_BRACE_STATEMENT", "\\{tWHILE}\\{tLEFT_PAREN}\\{nEXPR}\\{tRIGHT_PAREN}\\{nMAYBE_BLOCK}"),
            getProduction("NON_BRACE_STATEMENT", "\\{nFUNCTION_DEF}"),

            getProduction("ATOMIC_STATEMENT", "\\{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "\\{nEXPR}\\{tASSIGNMENT}\\{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "\\{tBREAK}"),
            getProduction("ATOMIC_STATEMENT", "\\{tCONTINUE}"),
            getProduction("ATOMIC_STATEMENT", "\\{tRETURN_UNIT}"),
            getProduction("ATOMIC_STATEMENT", "\\{tRETURN}\\{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "\\{nVARIABLE_DECL}"),

            getProduction("EXPR", "\\{nEXPR2}"),
            getProduction("EXPR", "\\{nEXPR2}\\{tQUESTION_MARK}\\{nEXPR2}\\{tCOLON}\\{nEXPR}"),

            getProduction("EXPR2", "\\{nEXPR4}"),
            getProduction("EXPR2", "\\{nEXPR4}\\{tOR}\\{nEXPR2}"),

            getProduction("EXPR4", "\\{nEXPR8}"),
            getProduction("EXPR4", "\\{nEXPR8}\\{tAND}\\{nEXPR4}"),

            getProduction("EXPR8", "\\{nEXPR16}"),
            getProduction("EXPR8", "\\{nEXPR16}\\{tXOR}\\{nEXPR8}"),
            getProduction("EXPR8", "\\{nEXPR16}\\{tIFF}\\{nEXPR8}"),

            getProduction("EXPR16", "\\{nEXPR32}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tEQUAL}\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tNOT_EQUAL}\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tLESS_THAN}\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tLESS_THAN_EQ}\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tGREATER_THAN}\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tGREATER_THAN_EQ}\\{nEXPR16}"),

            getProduction("EXPR32", "\\{nEXPR64}"),
            getProduction("EXPR32", "\\{nEXPR64}\\{tBIT_OR}\\{nEXPR32}"),

            getProduction("EXPR64", "\\{nEXPR128}"),
            getProduction("EXPR64", "\\{nEXPR128}\\{tBIT_XOR}\\{nEXPR64}"),

            getProduction("EXPR128", "\\{nEXPR256}"),
            getProduction("EXPR128", "\\{nEXPR256}\\{tBIT_AND}\\{nEXPR128}"),
        )
        return Grammar(SymbolRegexParser.getSymbolFromString("nPROGRAM"), productions)
    }
}
