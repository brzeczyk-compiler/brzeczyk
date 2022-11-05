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
            // main constructions
            getProduction("PROGRAM", "(\\{nFUNC_DEF}|(\\{nVAR_DECL}(\\{tNEWLINE}|\\{tSEMICOLON}))|\\{tNEWLINE})*"),

            getProduction("TYPE", "\\{tTYPE_INTEGER}|\\{tTYPE_BOOLEAN}|\\{tTYPE_UNIT}"),
            getProduction("CONST", "\\{tINTEGER}|\\{tTRUE_CONSTANT}|\\{tFALSE_CONSTANT}"),

            getProduction("VAR_DECL", "(\\{tVARIABLE}\\{tVALUE}\\{tCONSTANT})\\{tIDENTIFIER}\\{tCOLON}\\{nTYPE}(\\{tASSIGNMENT}\\{nEXPR})?"),
            getProduction("FUNC_DEF", "\\{tFUNCTION}\\{tIDENTIFIER}\\{tLEFT_PAREN}\\{nDEF_ARGS}\\{tRIGHT_PAREN}(\\{tARROW})?"),

            getProduction("DEF_ARGS", "(\\{nDEF_ARG}(\\{tCOMMA}\\{nDEF_ARG})*(\\{tCOMMA}\\{nDEF_ARG}\\{tASSIGNMENT}\\{nEXPR})*)?"),
            getProduction("DEF_ARGS", "\\{nDEF_ARG}\\{tASSIGNMENT}\\{nEXPR}(\\{tCOMMA}\\{nDEF_ARG}\\{tASSIGNMENT}\\{nEXPR})*"),
            getProduction("DEF_ARG", "\\{tIDENTIFIER}\\{tCOLON}\\{nTYPE}"),
            getProduction("CALL_ARGS", "(\\{nEXPR}(\\{tCOMMA}\\{nEXPR})*(\\{tCOMMA}\\{nEXPR}\\{tASSIGNMENT}\\{nEXPR})*)?"),
            getProduction("CALL_ARGS", "\\{nEXPR}\\{tASSIGNMENT}\\{nEXPR}(\\{tCOMMA}\\{nEXPR}\\{tASSIGNMENT}\\{nEXPR})*"),

            // expressions
            getProduction("EXPR2048", "\\{tLEFT_PAREN}\\{nEXPR}\\{tRIGHT_PAREN}"),
            getProduction("EXPR2048", "\\{nCONST}"),
            getProduction("EXPR2048", "\\{tIDENTIFIER}"),
            getProduction("EXPR2048", "\\{tIDENTIFIER}\\{tLEFT_PAREN}\\{nCALL_ARGS}\\{tRIGHT_PAREN}"),
            getProduction("EXPR2048", "\\{tMINUS}\\{EXPR2048}"),
            getProduction("EXPR2048", "\\{tINCREMENT}\\{tIDENTIFIER}"),
            getProduction("EXPR2048", "\\{tIDENTIFIER}\\{tINCREMENT}"),
            getProduction("EXPR2048", "\\{tDECREMENT}\\{tIDENTIFIER}"),
            getProduction("EXPR2048", "\\{tIDENTIFIER}\\{tDECREMENT}"),

            getProduction("EXPR1024", "\\{nEXPR2048}"),
            getProduction("EXPR1024", "\\{nEXPR2048}\\{tMULTIPLY}\\{nEXPR1024}"),
            getProduction("EXPR1024", "\\{nEXPR2048}\\{tDIVIDE}\\{nEXPR1024}"),
            getProduction("EXPR1024", "\\{nEXPR2048}\\{tMODULO}\\{nEXPR1024}"),

            getProduction("EXPR512", "\\{nEXPR1024}"),
            getProduction("EXPR512", "\\{nEXPR1024}\\{tPLUS}\\{nEXPR512}"),
            getProduction("EXPR512", "\\{nEXPR1024}\\{tMINUS}\\{nEXPR512}"),

            getProduction("EXPR256", "\\{nEXPR512}"),
            getProduction("EXPR256", "\\{nEXPR512}\\{tSHIFT_LEFT}\\{nEXPR256}"),
            getProduction("EXPR256", "\\{nEXPR512}\\{tSHIFT_RIGHT}\\{nEXPR256}"),

            getProduction("EXPR128", "\\{nEXPR256}"),
            getProduction("EXPR128", "\\{nEXPR256}\\{tBIT_AND}\\{nEXPR128}"),

            getProduction("EXPR64", "\\{nEXPR128}"),
            getProduction("EXPR64", "\\{nEXPR128}\\{tBIT_XOR}\\{nEXPR64}"),

            getProduction("EXPR32", "\\{nEXPR64}"),
            getProduction("EXPR32", "\\{nEXPR64}\\{tBIT_OR}\\{nEXPR32}"),

            getProduction("EXPR16", "\\{nEXPR32}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tEQUAL}\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tNOT_EQUAL}\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tLESS_THAN}\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tLESS_THAN_EQ}\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tGREATER_THAN}\\{nEXPR16}"),
            getProduction("EXPR16", "\\{nEXPR32}\\{tGREATER_THAN_EQ}\\{nEXPR16}"),

            getProduction("EXPR8", "\\{nEXPR16}"),
            getProduction("EXPR8", "\\{nEXPR16}\\{tXOR}\\{nEXPR8}"),
            getProduction("EXPR8", "\\{nEXPR16}\\{tIFF}\\{nEXPR8}"),

            getProduction("EXPR4", "\\{nEXPR8}"),
            getProduction("EXPR4", "\\{nEXPR8}\\{tAND}\\{nEXPR4}"),

            getProduction("EXPR2", "\\{nEXPR4}"),
            getProduction("EXPR2", "\\{nEXPR4}\\{tOR}\\{nEXPR2}"),

            getProduction("EXPR", "\\{nEXPR2}"),
            getProduction("EXPR", "\\{nEXPR2}\\{tQUESTION_MARK}\\{nEXPR2}\\{tCOLON}\\{nEXPR}"),

            // statements
            getProduction("MANY_STATEMENTS", "(\\{nSTATEMENT}|\\{tNEWLINE})*"),

            getProduction("MAYBE_BLOCK", "\\{tNEWLINE}*\\{nNON_BRACE_STATEMENT}"),
            getProduction("MAYBE_BLOCK", "\\{tLEFT_BRACE}\\{nMANY_STATEMENTS}\\{tRIGHT_BRACE}"),

            getProduction("STATEMENT", "\\{nNON_BRACE_STATEMENT}"),
            getProduction("STATEMENT", "\\{tLEFT_BRACE}\\{nMANY_STATEMENTS}\\{tRIGHT_BRACE}"),

            getProduction("NON_BRACE_STATEMENT", "\\{nATOMIC_STATEMENT}(\\{tNEWLINE}|\\{tSEMICOLON})"),
            getProduction("NON_BRACE_STATEMENT", "\\{tIF}\\{tLEFT_PAREN}\\{nEXPR}\\{tRIGHT_PAREN}\\{nMAYBE_BLOCK}(\\{tELSE_IF}\\{tLEFT_PAREN}\\{nEXPR}\\{tRIGHT_PAREN}\\{nMAYBE_BLOCK})*(\\{tELSE}\\{nMAYBE_BLOCK})?"),
            getProduction("NON_BRACE_STATEMENT", "\\{tWHILE}\\{tLEFT_PAREN}\\{nEXPR}\\{tRIGHT_PAREN}\\{nMAYBE_BLOCK}"),
            getProduction("NON_BRACE_STATEMENT", "\\{nFUNC_DEF}"),

            getProduction("ATOMIC_STATEMENT", "\\{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "\\{nEXPR}\\{tASSIGNMENT}\\{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "\\{tBREAK}"),
            getProduction("ATOMIC_STATEMENT", "\\{tCONTINUE}"),
            getProduction("ATOMIC_STATEMENT", "\\{tRETURN_UNIT}"),
            getProduction("ATOMIC_STATEMENT", "\\{tRETURN}\\{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "\\{nVAR_DECL}")
        )
        return Grammar(SymbolRegexParser.getSymbolFromString("nPROGRAM"), productions)
    }
}
