package compiler.parser.grammar

object ParserGrammar {

    private fun getProduction(lhs: String, regex: String): Production<Symbol> {
        return Production(
            SymbolRegexParser.getSymbolFromString("n$lhs"),
            SymbolRegexParser.parseStringToRegex(regex)
        )
    }

    private const val NLS: String = "(\\{NEWLINE})*" // NLS = NewLine Star

    fun getGrammar(): Grammar<Symbol> {
        val productions = listOf(
            // main constructions
            getProduction("PROGRAM", "(\\{nFUNC_DEF}|(\\{nVAR_DECL}(\\{tNEWLINE}|\\{tSEMICOLON}))|\\{tNEWLINE})*"),

            getProduction("TYPE", "\\{tTYPE_INTEGER}|\\{tTYPE_BOOLEAN}|\\{tTYPE_UNIT}"),
            getProduction("CONST", "\\{tINTEGER}|\\{tTRUE_CONSTANT}|\\{tFALSE_CONSTANT}"),

            getProduction("VAR_DECL", "(\\{tVARIABLE}|\\{tVALUE}|\\{tCONSTANT})$NLS\\{tIDENTIFIER}$NLS\\{tCOLON}$NLS\\{nTYPE}($NLS\\{tASSIGNMENT}$NLS\\{nEXPR})?"),
            getProduction("FUNC_DEF", "\\{tFUNCTION}$NLS\\{tIDENTIFIER}$NLS\\{tLEFT_PAREN}$NLS\\{nDEF_ARGS}$NLS\\{tRIGHT_PAREN}($NLS\\{tARROW}$NLS\\{nTYPE})?\\{LEFT_BRACE}\\{MANY_STATEMENTS}\\{RIGHT_BRACE}"),

            getProduction("DEF_ARGS", "(\\{nDEF_ARG}($NLS\\{tCOMMA}$NLS\\{nDEF_ARG})*($NLS\\{tCOMMA}$NLS\\{nDEF_ARG}$NLS\\{tASSIGNMENT}$NLS\\{nEXPR})*)?"),
            getProduction("DEF_ARGS", "\\{nDEF_ARG}$NLS\\{tASSIGNMENT}$NLS\\{nEXPR}($NLS\\{tCOMMA}$NLS\\{nDEF_ARG}$NLS\\{tASSIGNMENT}$NLS\\{nEXPR})*"),
            getProduction("DEF_ARG", "\\{tIDENTIFIER}$NLS\\{tCOLON}$NLS\\{nTYPE}"),
            getProduction("CALL_ARGS", "(\\{nEXPR}($NLS\\{tCOMMA}$NLS\\{nEXPR})*($NLS\\{tCOMMA}$NLS\\{nEXPR}$NLS\\{tASSIGNMENT}$NLS\\{nEXPR})*)?"),
            getProduction("CALL_ARGS", "\\{nEXPR}$NLS\\{tASSIGNMENT}$NLS\\{nEXPR}($NLS\\{tCOMMA}$NLS\\{nEXPR}$NLS\\{tASSIGNMENT}$NLS\\{nEXPR})*"),

            // expressions
            getProduction("EXPR2048", "\\{tLEFT_PAREN}$NLS\\{nEXPR}$NLS\\{tRIGHT_PAREN}"),
            getProduction("EXPR2048", "\\{nCONST}"),
            getProduction("EXPR2048", "\\{tIDENTIFIER}"),
            getProduction("EXPR2048", "\\{tIDENTIFIER}$NLS\\{tLEFT_PAREN}$NLS\\{nCALL_ARGS}$NLS\\{tRIGHT_PAREN}"),
            getProduction("EXPR2048", "\\{tMINUS}$NLS\\{EXPR2048}"),
            getProduction("EXPR2048", "\\{tINCREMENT}$NLS\\{tIDENTIFIER}"),
            getProduction("EXPR2048", "\\{tIDENTIFIER}$NLS\\{tINCREMENT}"),
            getProduction("EXPR2048", "\\{tDECREMENT}$NLS\\{tIDENTIFIER}"),
            getProduction("EXPR2048", "\\{tIDENTIFIER}$NLS\\{tDECREMENT}"),

            getProduction("EXPR1024", "\\{nEXPR2048}"),
            getProduction("EXPR1024", "\\{nEXPR2048}$NLS\\{tMULTIPLY}$NLS\\{nEXPR1024}"),
            getProduction("EXPR1024", "\\{nEXPR2048}$NLS\\{tDIVIDE}$NLS\\{nEXPR1024}"),
            getProduction("EXPR1024", "\\{nEXPR2048}$NLS\\{tMODULO}$NLS\\{nEXPR1024}"),

            getProduction("EXPR512", "\\{nEXPR1024}"),
            getProduction("EXPR512", "\\{nEXPR1024}$NLS\\{tPLUS}$NLS\\{nEXPR512}"),
            getProduction("EXPR512", "\\{nEXPR1024}$NLS\\{tMINUS}$NLS\\{nEXPR512}"),

            getProduction("EXPR256", "\\{nEXPR512}"),
            getProduction("EXPR256", "\\{nEXPR512}$NLS\\{tSHIFT_LEFT}$NLS\\{nEXPR256}"),
            getProduction("EXPR256", "\\{nEXPR512}$NLS\\{tSHIFT_RIGHT}$NLS\\{nEXPR256}"),

            getProduction("EXPR128", "\\{nEXPR256}"),
            getProduction("EXPR128", "\\{nEXPR256}$NLS\\{tBIT_AND}$NLS\\{nEXPR128}"),

            getProduction("EXPR64", "\\{nEXPR128}"),
            getProduction("EXPR64", "\\{nEXPR128}$NLS\\{tBIT_XOR}$NLS\\{nEXPR64}"),

            getProduction("EXPR32", "\\{nEXPR64}"),
            getProduction("EXPR32", "\\{nEXPR64}$NLS\\{tBIT_OR}$NLS\\{nEXPR32}"),

            getProduction("EXPR16", "\\{nEXPR32}"),
            getProduction("EXPR16", "\\{nEXPR32}$NLS\\{tEQUAL}$NLS\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}$NLS\\{tNOT_EQUAL}$NLS\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}$NLS\\{tLESS_THAN}$NLS\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}$NLS\\{tLESS_THAN_EQ}$NLS\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}$NLS\\{tGREATER_THAN}$NLS\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}$NLS\\{tGREATER_THAN_EQ}$NLS\\{nEXPR16}"),

            getProduction("EXPR8", "\\{nEXPR16}"),
            getProduction("EXPR8", "\\{nEXPR16}$NLS\\{tXOR}$NLS\\{nEXPR8}"),
            getProduction("EXPR8", "\\{nEXPR16}$NLS\\{tIFF}$NLS\\{nEXPR8}"),

            getProduction("EXPR4", "\\{nEXPR8}"),
            getProduction("EXPR4", "\\{nEXPR8}$NLS\\{tAND}$NLS\\{nEXPR4}"),

            getProduction("EXPR2", "\\{nEXPR4}"),
            getProduction("EXPR2", "\\{nEXPR4}$NLS\\{tOR}$NLS\\{nEXPR2}"),

            getProduction("EXPR", "\\{nEXPR2}"),
            getProduction("EXPR", "\\{nEXPR2}$NLS\\{tQUESTION_MARK}$NLS\\{nEXPR2}$NLS\\{tCOLON}$NLS\\{nEXPR}"),

            // statements
            getProduction("MANY_STATEMENTS", "(\\{nSTATEMENT}|\\{tNEWLINE})*"),

            getProduction("MAYBE_BLOCK", "$NLS\\{nNON_BRACE_STATEMENT}"),
            getProduction("MAYBE_BLOCK", "\\{tLEFT_BRACE}\\{nMANY_STATEMENTS}\\{tRIGHT_BRACE}"),

            getProduction("STATEMENT", "\\{nNON_BRACE_STATEMENT}"),
            getProduction("STATEMENT", "\\{tLEFT_BRACE}\\{nMANY_STATEMENTS}\\{tRIGHT_BRACE}"),

            getProduction("NON_BRACE_STATEMENT", "\\{nATOMIC_STATEMENT}(\\{tNEWLINE}|\\{tSEMICOLON})"),
            getProduction("NON_BRACE_STATEMENT", "\\{tIF}$NLS\\{tLEFT_PAREN}$NLS\\{nEXPR}$NLS\\{tRIGHT_PAREN}\\{nMAYBE_BLOCK}($NLS\\{tELSE_IF}$NLS\\{tLEFT_PAREN}$NLS\\{nEXPR}$NLS\\{tRIGHT_PAREN}\\{nMAYBE_BLOCK})*($NLS\\{tELSE}\\{nMAYBE_BLOCK})?"),
            getProduction("NON_BRACE_STATEMENT", "\\{tWHILE}$NLS\\{tLEFT_PAREN}$NLS\\{nEXPR}$NLS\\{tRIGHT_PAREN}\\{nMAYBE_BLOCK}"),
            getProduction("NON_BRACE_STATEMENT", "\\{nFUNC_DEF}"),

            getProduction("ATOMIC_STATEMENT", "\\{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "\\{nEXPR}$NLS\\{tASSIGNMENT}$NLS\\{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "\\{tBREAK}"),
            getProduction("ATOMIC_STATEMENT", "\\{tCONTINUE}"),
            getProduction("ATOMIC_STATEMENT", "\\{tRETURN_UNIT}"),
            getProduction("ATOMIC_STATEMENT", "\\{tRETURN}$NLS\\{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "\\{nVAR_DECL}")
        )
        return Grammar(SymbolRegexParser.getSymbolFromString("nPROGRAM"), productions)
    }
}
