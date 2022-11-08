package compiler.parser.grammar

object ParserGrammar {

    private fun getProduction(lhs: String, regex: String): Production<Symbol> {
        return Production(
            SymbolRegexParser.getSymbolFromString("n$lhs"),
            SymbolRegexParser.parseStringToRegex(regex)
        )
    }

    private const val NLS: String = "({tNEWLINE})*" // NLS = NewLine Star

    fun getGrammar(): Grammar<Symbol> {
        val productions = listOf(
            // main constructions
            getProduction("PROGRAM", "({nFUNC_DEF}|({nVAR_DECL}({tNEWLINE}|{tSEMICOLON}))|{tNEWLINE})*"),

            getProduction("TYPE", "{tTYPE_INTEGER}|{tTYPE_BOOLEAN}|{tTYPE_UNIT}"),
            getProduction("CONST", "{tINTEGER}|{tTRUE_CONSTANT}|{tFALSE_CONSTANT}"),

            getProduction("VAR_DECL", "({tVARIABLE}|{tVALUE}|{tCONSTANT})$NLS{tIDENTIFIER}$NLS{tCOLON}$NLS{nTYPE}({tASSIGNMENT}$NLS{nEXPR})?"),
            getProduction("FUNC_DEF", "{tFUNCTION}$NLS{tIDENTIFIER}$NLS{tLEFT_PAREN}$NLS{nDEF_ARGS}{tRIGHT_PAREN}($NLS{tARROW}$NLS{nTYPE})?{tLEFT_BRACE}{nMANY_STATEMENTS}{tRIGHT_BRACE}"),

            getProduction("DEF_ARGS", "({nDEF_ARG}($NLS{tCOMMA}$NLS{nDEF_ARG})*$NLS({tCOMMA}$NLS{nDEF_ARG}$NLS{tASSIGNMENT}$NLS{nE_EXPR})*)?"),
            getProduction("DEF_ARGS", "{nDEF_ARG}$NLS{tASSIGNMENT}$NLS{nE_EXPR}({tCOMMA}$NLS{nDEF_ARG}$NLS{tASSIGNMENT}$NLS{nE_EXPR})*"),
            getProduction("DEF_ARG", "{tIDENTIFIER}$NLS{tCOLON}$NLS{nTYPE}"),
            getProduction("CALL_ARGS", "({nE_EXPR}({tCOMMA}$NLS{nE_EXPR})*({tCOMMA}$NLS{nE_EXPR}{tASSIGNMENT}$NLS{nE_EXPR})*)?"),
            getProduction("CALL_ARGS", "{nE_EXPR}{tASSIGNMENT}$NLS{nE_EXPR}({tCOMMA}$NLS{nE_EXPR}{tASSIGNMENT}$NLS{nE_EXPR})*"),

            // expressions
            getProduction("EXPR2048", "{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}"),
            getProduction("EXPR2048", "{nCONST}"),
            getProduction("EXPR2048", "{tIDENTIFIER}"),
            getProduction("EXPR2048", "{tIDENTIFIER}{tLEFT_PAREN}$NLS{nCALL_ARGS}{tRIGHT_PAREN}"),
            getProduction("EXPR2048", "{tMINUS}$NLS{nEXPR2048}"),
            getProduction("EXPR2048", "{tINCREMENT}{tIDENTIFIER}"),
            getProduction("EXPR2048", "{tIDENTIFIER}{tINCREMENT}"),
            getProduction("EXPR2048", "{tDECREMENT}{tIDENTIFIER}"),
            getProduction("EXPR2048", "{tIDENTIFIER}{tDECREMENT}"),

            getProduction("EXPR1024", "{nEXPR2048}"),
            getProduction("EXPR1024", "{nEXPR2048}{tMULTIPLY}$NLS{nEXPR1024}"),
            getProduction("EXPR1024", "{nEXPR2048}{tDIVIDE}$NLS{nEXPR1024}"),
            getProduction("EXPR1024", "{nEXPR2048}{tMODULO}$NLS{nEXPR1024}"),

            getProduction("EXPR512", "{nEXPR1024}"),
            getProduction("EXPR512", "{nEXPR1024}{tPLUS}$NLS{nEXPR512}"),
            getProduction("EXPR512", "{nEXPR1024}{tMINUS}$NLS{nEXPR512}"),

            getProduction("EXPR256", "{nEXPR512}"),
            getProduction("EXPR256", "{nEXPR512}{tSHIFT_LEFT}$NLS{nEXPR256}"),
            getProduction("EXPR256", "{nEXPR512}{tSHIFT_RIGHT}$NLS{nEXPR256}"),

            getProduction("EXPR128", "{nEXPR256}"),
            getProduction("EXPR128", "{nEXPR256}{tBIT_AND}$NLS{nEXPR128}"),

            getProduction("EXPR64", "{nEXPR128}"),
            getProduction("EXPR64", "{nEXPR128}{tBIT_XOR}$NLS{nEXPR64}"),

            getProduction("EXPR32", "{nEXPR64}"),
            getProduction("EXPR32", "{nEXPR64}{tBIT_OR}$NLS{nEXPR32}"),

            getProduction("EXPR16", "{nEXPR32}"),
            getProduction("EXPR16", "{nEXPR32}{tEQUAL}$NLS{nEXPR16}"),
            getProduction("EXPR16", "{nEXPR32}{tNOT_EQUAL}$NLS{nEXPR16}"),
            getProduction("EXPR16", "{nEXPR32}{tLESS_THAN}$NLS{nEXPR16}"),
            getProduction("EXPR16", "{nEXPR32}{tLESS_THAN_EQ}$NLS{nEXPR16}"),
            getProduction("EXPR16", "{nEXPR32}{tGREATER_THAN}$NLS{nEXPR16}"),
            getProduction("EXPR16", "{nEXPR32}{tGREATER_THAN_EQ}$NLS{nEXPR16}"),

            getProduction("EXPR8", "{nEXPR16}"),
            getProduction("EXPR8", "{nEXPR16}{tXOR}$NLS{nEXPR8}"),
            getProduction("EXPR8", "{nEXPR16}{tIFF}$NLS{nEXPR8}"),

            getProduction("EXPR4", "{nEXPR8}"),
            getProduction("EXPR4", "{nEXPR8}{tAND}$NLS{nEXPR4}"),

            getProduction("EXPR2", "{nEXPR4}"),
            getProduction("EXPR2", "{nEXPR4}{tOR}$NLS{nEXPR2}"),

            getProduction("EXPR", "{nEXPR2}"),
            getProduction("EXPR", "{nEXPR2}{tQUESTION_MARK}$NLS{nEXPR2}$NLS{tCOLON}$NLS{nEXPR}"),

            // enclosed expressions
            getProduction("E_EXPR2048", "{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}$NLS"),
            getProduction("E_EXPR2048", "{nCONST}$NLS"),
            getProduction("E_EXPR2048", "{tIDENTIFIER}$NLS"),
            getProduction("E_EXPR2048", "{tIDENTIFIER}$NLS{tLEFT_PAREN}$NLS{nCALL_ARGS}{tRIGHT_PAREN}$NLS"),
            getProduction("E_EXPR2048", "{tMINUS}$NLS{nE_EXPR2048}"),
            getProduction("E_EXPR2048", "{tINCREMENT}$NLS{tIDENTIFIER}$NLS"),
            getProduction("E_EXPR2048", "{tIDENTIFIER}$NLS{tINCREMENT}$NLS"),
            getProduction("E_EXPR2048", "{tDECREMENT}$NLS{tIDENTIFIER}$NLS"),
            getProduction("E_EXPR2048", "{tIDENTIFIER}$NLS{tDECREMENT}$NLS"),

            getProduction("E_EXPR1024", "{nE_EXPR2048}"),
            getProduction("E_EXPR1024", "{nE_EXPR2048}{tMULTIPLY}$NLS{nE_EXPR1024}"),
            getProduction("E_EXPR1024", "{nE_EXPR2048}{tDIVIDE}$NLS{nE_EXPR1024}"),
            getProduction("E_EXPR1024", "{nE_EXPR2048}{tMODULO}$NLS{nE_EXPR1024}"),

            getProduction("E_EXPR512", "{nE_EXPR1024}"),
            getProduction("E_EXPR512", "{nE_EXPR1024}{tPLUS}$NLS{nE_EXPR512}"),
            getProduction("E_EXPR512", "{nE_EXPR1024}{tMINUS}$NLS{nE_EXPR512}"),

            getProduction("E_EXPR256", "{nE_EXPR512}"),
            getProduction("E_EXPR256", "{nE_EXPR512}{tSHIFT_LEFT}$NLS{nE_EXPR256}"),
            getProduction("E_EXPR256", "{nE_EXPR512}{tSHIFT_RIGHT}$NLS{nE_EXPR256}"),

            getProduction("E_EXPR128", "{nE_EXPR256}"),
            getProduction("E_EXPR128", "{nE_EXPR256}{tBIT_AND}$NLS{nE_EXPR128}"),

            getProduction("E_EXPR64", "{nE_EXPR128}"),
            getProduction("E_EXPR64", "{nE_EXPR128}{tBIT_XOR}$NLS{nE_EXPR64}"),

            getProduction("E_EXPR32", "{nE_EXPR64}"),
            getProduction("E_EXPR32", "{nE_EXPR64}{tBIT_OR}$NLS{nE_EXPR32}"),

            getProduction("E_EXPR16", "{nE_EXPR32}"),
            getProduction("E_EXPR16", "{nE_EXPR32}{tEQUAL}$NLS{nE_EXPR16}"),
            getProduction("E_EXPR16", "{nE_EXPR32}{tNOT_EQUAL}$NLS{nE_EXPR16}"),
            getProduction("E_EXPR16", "{nE_EXPR32}{tLESS_THAN}$NLS{nE_EXPR16}"),
            getProduction("E_EXPR16", "{nE_EXPR32}{tLESS_THAN_EQ}$NLS{nE_EXPR16}"),
            getProduction("E_EXPR16", "{nE_EXPR32}{tGREATER_THAN}$NLS{nE_EXPR16}"),
            getProduction("E_EXPR16", "{nE_EXPR32}{tGREATER_THAN_EQ}$NLS{nE_EXPR16}"),

            getProduction("E_EXPR8", "{nE_EXPR16}"),
            getProduction("E_EXPR8", "{nE_EXPR16}{tXOR}$NLS{nE_EXPR8}"),
            getProduction("E_EXPR8", "{nE_EXPR16}{tIFF}$NLS{nE_EXPR8}"),

            getProduction("E_EXPR4", "{nE_EXPR8}"),
            getProduction("E_EXPR4", "{nE_EXPR8}{tAND}$NLS{nE_EXPR4}"),

            getProduction("E_EXPR2", "{nE_EXPR4}"),
            getProduction("E_EXPR2", "{nE_EXPR4}{tOR}$NLS{nE_EXPR2}"),

            getProduction("E_EXPR", "{nE_EXPR2}"),
            getProduction("E_EXPR", "{nE_EXPR2}{tQUESTION_MARK}$NLS{nE_EXPR2}{tCOLON}$NLS{nE_EXPR}"),

            // statements
            getProduction("MANY_STATEMENTS", "$NLS{nSTATEMENT}*"),

            getProduction("MAYBE_BLOCK", "$NLS{nNON_BRACE_STATEMENT}"),
            getProduction("MAYBE_BLOCK", "{tLEFT_BRACE}{nMANY_STATEMENTS}{tRIGHT_BRACE}$NLS"),

            getProduction("NON_IF_MAYBE_BLOCK", "$NLS{nNON_IF_NON_BRACE_STATEMENT}"),
            getProduction("NON_IF_MAYBE_BLOCK", "{tLEFT_BRACE}{nMANY_STATEMENTS}{tRIGHT_BRACE}$NLS"),

            getProduction("STATEMENT", "{nNON_BRACE_STATEMENT}"),
            getProduction("STATEMENT", "{tLEFT_BRACE}{nMANY_STATEMENTS}{tRIGHT_BRACE}$NLS"),

            getProduction("NON_BRACE_STATEMENT", "{nATOMIC_STATEMENT}({tNEWLINE}|{tSEMICOLON})$NLS"),
            getProduction("NON_BRACE_STATEMENT", "{tIF}$NLS{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}{nNON_IF_MAYBE_BLOCK}({tELSE_IF}$NLS{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}{nNON_IF_MAYBE_BLOCK})*({tELSE}{nMAYBE_BLOCK})?"),
            getProduction("NON_BRACE_STATEMENT", "{tWHILE}$NLS{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}{nMAYBE_BLOCK}"),
            getProduction("NON_BRACE_STATEMENT", "{nFUNC_DEF}$NLS"),

            getProduction("NON_IF_NON_BRACE_STATEMENT", "{nATOMIC_STATEMENT}({tNEWLINE}|{tSEMICOLON})$NLS"),
            getProduction("NON_IF_NON_BRACE_STATEMENT", "{tWHILE}$NLS{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}{nNON_IF_MAYBE_BLOCK}"),
            getProduction("NON_IF_NON_BRACE_STATEMENT", "{nFUNC_DEF}$NLS"),

            getProduction("ATOMIC_STATEMENT", "{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "{nEXPR}{tASSIGNMENT}$NLS{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "{tBREAK}"),
            getProduction("ATOMIC_STATEMENT", "{tCONTINUE}"),
            getProduction("ATOMIC_STATEMENT", "{tRETURN_UNIT}"),
            getProduction("ATOMIC_STATEMENT", "{tRETURN}$NLS{nEXPR}"),
            getProduction("ATOMIC_STATEMENT", "{nVAR_DECL}")
        )
        return Grammar(SymbolRegexParser.getSymbolFromString("nPROGRAM"), productions)
    }
}
