package compiler.syntax

import compiler.grammar.Grammar
import compiler.grammar.Production
import compiler.syntax.utils.SymbolRegexParser

object LanguageGrammar {

    private fun getProduction(lhs: String, regex: String): Production<Symbol> {
        return Production(
            SymbolRegexParser.getSymbolFromString("n$lhs"),
            SymbolRegexParser.parseStringToRegex(regex)
        )
    }

    private const val NLS: String = "({tNEWLINE})*" // NLS = NewLine Star

    object Productions {
        val program = getProduction("PROGRAM", "({nFUNC_DEF}|(({nVAR_DECL}|{nFOREIGN_DECL})({tNEWLINE}|{tSEMICOLON}))|{tNEWLINE})*")

        val type = getProduction("TYPE", "{tTYPE_INTEGER}|{tTYPE_BOOLEAN}|{tTYPE_UNIT}|{tTYPE_STRING}")
        val tableType = getProduction("TYPE", "{tLEFT_BRACKET}$NLS{nTYPE}$NLS{tRIGHT_BRACKET}")
        val const = getProduction("CONST", "{tINTEGER}|{tSTRING}|{tTRUE_CONSTANT}|{tFALSE_CONSTANT}|{tUNIT_CONSTANT}")

        val varDecl = getProduction("VAR_DECL", "({tVARIABLE}|{tVALUE}|{tCONSTANT})$NLS{tIDENTIFIER}$NLS{tCOLON}$NLS{nTYPE}({tASSIGNMENT}$NLS{nEXPR})?")
        val funcDef = getProduction("FUNC_DEF", "({tFUNCTION}|{tGENERATOR})$NLS{tIDENTIFIER}$NLS{tLEFT_PAREN}$NLS{nDEF_ARGS}{tRIGHT_PAREN}($NLS{tARROW}$NLS{nTYPE})?{tLEFT_BRACE}{nMANY_STATEMENTS}{tRIGHT_BRACE}")
        val foreignDecl = getProduction("FOREIGN_DECL", "({tFOREIGN1}|{tFOREIGN2})$NLS({tFUNCTION}|{tGENERATOR})$NLS({tIDENTIFIER}|{tFOREIGN_NAME})$NLS{tLEFT_PAREN}$NLS{nDEF_ARGS}{tRIGHT_PAREN}({tARROW}$NLS{nTYPE})?({tAS}$NLS{tIDENTIFIER})?")

        val defArgs1 = getProduction("DEF_ARGS", "({nDEF_ARG}($NLS{tCOMMA}$NLS{nDEF_ARG})*$NLS({tCOMMA}$NLS{nDEF_ARG}$NLS{tASSIGNMENT}$NLS{nE_EXPR})*)?")
        val defArgs2 = getProduction("DEF_ARGS", "{nDEF_ARG}$NLS{tASSIGNMENT}$NLS{nE_EXPR}({tCOMMA}$NLS{nDEF_ARG}$NLS{tASSIGNMENT}$NLS{nE_EXPR})*")
        val defArg = getProduction("DEF_ARG", "{tIDENTIFIER}$NLS{tCOLON}$NLS{nTYPE}")
        val callArgs1 = getProduction("CALL_ARGS", "({nE_EXPR}({tCOMMA}$NLS{nE_EXPR})*({tCOMMA}$NLS{nE_EXPR}{tASSIGNMENT}$NLS{nE_EXPR})*)?")
        val callArgs2 = getProduction("CALL_ARGS", "{nE_EXPR}{tASSIGNMENT}$NLS{nE_EXPR}({tCOMMA}$NLS{nE_EXPR}{tASSIGNMENT}$NLS{nE_EXPR})*")

        // expressions
        val expr4096Parenthesis = getProduction("EXPR4096", "{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}")
        val expr4096Const = getProduction("EXPR4096", "{nCONST}")
        val expr4096Identifier = getProduction("EXPR4096", "{tIDENTIFIER}")
        val expr4096Call = getProduction("EXPR4096", "{tIDENTIFIER}{tLEFT_PAREN}$NLS{nCALL_ARGS}{tRIGHT_PAREN}")
        val expr4096ArrayDefaultAllocation = getProduction("EXPR4096", "{tARRAY_ALLOCATION}{nTYPE}{tLEFT_BRACKET}$NLS{nE_EXPR}{tRIGHT_BRACKET}{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}")
        val expr4096ArrayListAllocation = getProduction("EXPR4096", "{tARRAY_ALLOCATION}{nTYPE}{tLEFT_BRACE}$NLS{nE_EXPR}({tCOMMA}$NLS{nE_EXPR})*{tRIGHT_BRACE}")

        val expr2048PassThrough = getProduction("EXPR2048", "{nEXPR4096}")
        val expr2048ArrayAccess = getProduction("EXPR2048", "{nEXPR4096}{tLEFT_BRACKET}$NLS{nE_EXPR}{tRIGHT_BRACKET}({tLEFT_BRACKET}$NLS{nE_EXPR}{tRIGHT_BRACKET})*")
        val expr2048ArrayLength = getProduction("EXPR2048", "{tLENGTH}$NLS{nEXPR2048}")
        val expr2048UnaryPlus = getProduction("EXPR2048", "{tPLUS}$NLS{nEXPR2048}")
        val expr2048UnaryMinus = getProduction("EXPR2048", "{tMINUS}$NLS{nEXPR2048}")
        val expr2048UnaryBoolNot = getProduction("EXPR2048", "{tNOT}$NLS{nEXPR2048}")
        val expr2048UnaryBitNot = getProduction("EXPR2048", "{tBIT_NOT}$NLS{nEXPR2048}")

        val expr1024PassThrough = getProduction("EXPR1024", "{nEXPR2048}")
        val expr1024Multiply = getProduction("EXPR1024", "{nEXPR2048}{tMULTIPLY}$NLS{nEXPR1024}")
        val expr1024Divide = getProduction("EXPR1024", "{nEXPR2048}{tDIVIDE}$NLS{nEXPR1024}")
        val expr1024Modulo = getProduction("EXPR1024", "{nEXPR2048}{tMODULO}$NLS{nEXPR1024}")

        val expr512PassThrough = getProduction("EXPR512", "{nEXPR1024}")
        val expr512Plus = getProduction("EXPR512", "{nEXPR1024}{tPLUS}$NLS{nEXPR512}")
        val expr512Minus = getProduction("EXPR512", "{nEXPR1024}{tMINUS}$NLS{nEXPR512}")

        val expr256PassThrough = getProduction("EXPR256", "{nEXPR512}")
        val expr256ShiftLeft = getProduction("EXPR256", "{nEXPR512}{tSHIFT_LEFT}$NLS{nEXPR256}")
        val expr256ShiftRight = getProduction("EXPR256", "{nEXPR512}{tSHIFT_RIGHT}$NLS{nEXPR256}")

        val expr128PassThrough = getProduction("EXPR128", "{nEXPR256}")
        val expr128BitAnd = getProduction("EXPR128", "{nEXPR256}{tBIT_AND}$NLS{nEXPR128}")

        val expr64PassThrough = getProduction("EXPR64", "{nEXPR128}")
        val expr64BitXor = getProduction("EXPR64", "{nEXPR128}{tBIT_XOR}$NLS{nEXPR64}")

        val expr32PassThrough = getProduction("EXPR32", "{nEXPR64}")
        val expr32BitOr = getProduction("EXPR32", "{nEXPR64}{tBIT_OR}$NLS{nEXPR32}")

        val expr16PassThrough = getProduction("EXPR16", "{nEXPR32}")
        val expr16Equal = getProduction("EXPR16", "{nEXPR32}{tEQUAL}$NLS{nEXPR16}")
        val expr16NotEqual = getProduction("EXPR16", "{nEXPR32}{tNOT_EQUAL}$NLS{nEXPR16}")
        val expr16LessThan = getProduction("EXPR16", "{nEXPR32}{tLESS_THAN}$NLS{nEXPR16}")
        val expr16LessOrEq = getProduction("EXPR16", "{nEXPR32}{tLESS_THAN_EQ}$NLS{nEXPR16}")
        val expr16GreaterThan = getProduction("EXPR16", "{nEXPR32}{tGREATER_THAN}$NLS{nEXPR16}")
        val expr16GreaterOrEq = getProduction("EXPR16", "{nEXPR32}{tGREATER_THAN_EQ}$NLS{nEXPR16}")

        val expr8PassThrough = getProduction("EXPR8", "{nEXPR16}")
        val expr8BoolXor = getProduction("EXPR8", "{nEXPR16}{tXOR}$NLS{nEXPR8}")
        val expr8BoolIff = getProduction("EXPR8", "{nEXPR16}{tIFF}$NLS{nEXPR8}")

        val expr4PassThrough = getProduction("EXPR4", "{nEXPR8}")
        val expr4BoolAnd = getProduction("EXPR4", "{nEXPR8}{tAND}$NLS{nEXPR4}")

        val expr2PassThrough = getProduction("EXPR2", "{nEXPR4}")
        val expr2BoolOr = getProduction("EXPR2", "{nEXPR4}{tOR}$NLS{nEXPR2}")

        val exprPassThrough = getProduction("EXPR", "{nEXPR2}")
        val exprTernary = getProduction("EXPR", "{nEXPR2}{tQUESTION_MARK}$NLS{nEXPR2}$NLS{tCOLON}$NLS{nEXPR}")

        // enclosed expressions
        val eExpr4096Parenthesis = getProduction("E_EXPR4096", "{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}$NLS")
        val eExpr4096Const = getProduction("E_EXPR4096", "{nCONST}$NLS")
        val eExpr4096Identifier = getProduction("E_EXPR4096", "{tIDENTIFIER}$NLS")
        val eExpr4096Call = getProduction("E_EXPR4096", "{tIDENTIFIER}$NLS{tLEFT_PAREN}$NLS{nCALL_ARGS}{tRIGHT_PAREN}$NLS")
        val eExpr4096ArrayDefaultAllocation = getProduction("E_EXPR4096", "{tARRAY_ALLOCATION}{nTYPE}{tLEFT_BRACKET}$NLS{nE_EXPR}{tRIGHT_BRACKET}{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}$NLS")
        val eExpr4096ArrayListAllocation = getProduction("E_EXPR4096", "{tARRAY_ALLOCATION}{nTYPE}{tLEFT_BRACE}$NLS{nE_EXPR}({tCOMMA}$NLS{nE_EXPR})*{tRIGHT_BRACE}$NLS")

        val eExpr2048PassThrough = getProduction("E_EXPR2048", "{nE_EXPR4096}")
        val eExpr2048ArrayAccess = getProduction("E_EXPR2048", "{nE_EXPR4096}{tLEFT_BRACKET}$NLS{nE_EXPR}{tRIGHT_BRACKET}({tLEFT_BRACKET}$NLS{nE_EXPR}{tRIGHT_BRACKET})*$NLS")
        val eExpr2048ArrayLength = getProduction("E_EXPR2048", "{tLENGTH}$NLS{nE_EXPR2048}")
        val eExpr2048UnaryPlus = getProduction("E_EXPR2048", "{tPLUS}$NLS{nE_EXPR2048}")
        val eExpr2048UnaryMinus = getProduction("E_EXPR2048", "{tMINUS}$NLS{nE_EXPR2048}")
        val eExpr2048UnaryBoolNot = getProduction("E_EXPR2048", "{tNOT}$NLS{nE_EXPR2048}")
        val eExpr2048UnaryBitNot = getProduction("E_EXPR2048", "{tBIT_NOT}$NLS{nE_EXPR2048}")

        val eExpr1024PassThrough = getProduction("E_EXPR1024", "{nE_EXPR2048}")
        val eExpr1024Multiply = getProduction("E_EXPR1024", "{nE_EXPR2048}{tMULTIPLY}$NLS{nE_EXPR1024}")
        val eExpr1024Divide = getProduction("E_EXPR1024", "{nE_EXPR2048}{tDIVIDE}$NLS{nE_EXPR1024}")
        val eExpr1024Modulo = getProduction("E_EXPR1024", "{nE_EXPR2048}{tMODULO}$NLS{nE_EXPR1024}")

        val eExpr512PassThrough = getProduction("E_EXPR512", "{nE_EXPR1024}")
        val eExpr512Plus = getProduction("E_EXPR512", "{nE_EXPR1024}{tPLUS}$NLS{nE_EXPR512}")
        val eExpr512Minus = getProduction("E_EXPR512", "{nE_EXPR1024}{tMINUS}$NLS{nE_EXPR512}")

        val eExpr256PassThrough = getProduction("E_EXPR256", "{nE_EXPR512}")
        val eExpr256ShiftLeft = getProduction("E_EXPR256", "{nE_EXPR512}{tSHIFT_LEFT}$NLS{nE_EXPR256}")
        val eExpr256ShiftRight = getProduction("E_EXPR256", "{nE_EXPR512}{tSHIFT_RIGHT}$NLS{nE_EXPR256}")

        val eExpr128PassThrough = getProduction("E_EXPR128", "{nE_EXPR256}")
        val eExpr128BitAnd = getProduction("E_EXPR128", "{nE_EXPR256}{tBIT_AND}$NLS{nE_EXPR128}")

        val eExpr64PassThrough = getProduction("E_EXPR64", "{nE_EXPR128}")
        val eExpr64BitXor = getProduction("E_EXPR64", "{nE_EXPR128}{tBIT_XOR}$NLS{nE_EXPR64}")

        val eExpr32PassThrough = getProduction("E_EXPR32", "{nE_EXPR64}")
        val eExpr32BitOr = getProduction("E_EXPR32", "{nE_EXPR64}{tBIT_OR}$NLS{nE_EXPR32}")

        val eExpr16PassThrough = getProduction("E_EXPR16", "{nE_EXPR32}")
        val eExpr16Equal = getProduction("E_EXPR16", "{nE_EXPR32}{tEQUAL}$NLS{nE_EXPR16}")
        val eExpr16NotEqual = getProduction("E_EXPR16", "{nE_EXPR32}{tNOT_EQUAL}$NLS{nE_EXPR16}")
        val eExpr16LessThan = getProduction("E_EXPR16", "{nE_EXPR32}{tLESS_THAN}$NLS{nE_EXPR16}")
        val eExpr16LessOrEq = getProduction("E_EXPR16", "{nE_EXPR32}{tLESS_THAN_EQ}$NLS{nE_EXPR16}")
        val eExpr16GreaterThan = getProduction("E_EXPR16", "{nE_EXPR32}{tGREATER_THAN}$NLS{nE_EXPR16}")
        val eExpr16GreaterOrEq = getProduction("E_EXPR16", "{nE_EXPR32}{tGREATER_THAN_EQ}$NLS{nE_EXPR16}")

        val eExpr8PassThrough = getProduction("E_EXPR8", "{nE_EXPR16}")
        val eExpr8BoolXor = getProduction("E_EXPR8", "{nE_EXPR16}{tXOR}$NLS{nE_EXPR8}")
        val eExpr8BoolIff = getProduction("E_EXPR8", "{nE_EXPR16}{tIFF}$NLS{nE_EXPR8}")

        val eExpr4PassThrough = getProduction("E_EXPR4", "{nE_EXPR8}")
        val eExpr4BoolAnd = getProduction("E_EXPR4", "{nE_EXPR8}{tAND}$NLS{nE_EXPR4}")

        val eExpr2PassThrough = getProduction("E_EXPR2", "{nE_EXPR4}")
        val eExpr2BoolOr = getProduction("E_EXPR2", "{nE_EXPR4}{tOR}$NLS{nE_EXPR2}")

        val eExprPassThrough = getProduction("E_EXPR", "{nE_EXPR2}")
        val eExprTernary = getProduction("E_EXPR", "{nE_EXPR2}{tQUESTION_MARK}$NLS{nE_EXPR2}{tCOLON}$NLS{nE_EXPR}")

        // statements
        val manyStatements = getProduction("MANY_STATEMENTS", "$NLS{nSTATEMENT}*")

        val maybeBlockNonBrace = getProduction("MAYBE_BLOCK", "$NLS{nNON_BRACE_STATEMENT}")
        val maybeBlockBraces = getProduction("MAYBE_BLOCK", "{tLEFT_BRACE}{nMANY_STATEMENTS}{tRIGHT_BRACE}$NLS")

        val nonIfMaybeBlockNonBrace = getProduction("NON_IF_MAYBE_BLOCK", "$NLS{nNON_IF_NON_BRACE_STATEMENT}")
        val nonIfMaybeBlockBraces = getProduction("NON_IF_MAYBE_BLOCK", "{tLEFT_BRACE}{nMANY_STATEMENTS}{tRIGHT_BRACE}$NLS")

        val statementNonBrace = getProduction("STATEMENT", "{nNON_BRACE_STATEMENT}")
        val statementBraces = getProduction("STATEMENT", "{tLEFT_BRACE}{nMANY_STATEMENTS}{tRIGHT_BRACE}$NLS")

        val nonBraceStatementAtomic = getProduction("NON_BRACE_STATEMENT", "{nATOMIC_STATEMENT}({tNEWLINE}|{tSEMICOLON})$NLS")
        val nonBraceStatementIf = getProduction("NON_BRACE_STATEMENT", "{tIF}$NLS{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}{nNON_IF_MAYBE_BLOCK}({tELSE_IF}$NLS{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}{nNON_IF_MAYBE_BLOCK})*({tELSE}{nMAYBE_BLOCK})?")
        val nonBraceStatementWhile = getProduction("NON_BRACE_STATEMENT", "{tWHILE}$NLS{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}{nMAYBE_BLOCK}")
        val nonBraceStatementForEach = getProduction("NON_BRACE_STATEMENT", "{tFOR_EACH}$NLS{tIDENTIFIER}$NLS{tCOLON}$NLS{nTYPE}$NLS{tFROM}$NLS{tIDENTIFIER}{tLEFT_PAREN}$NLS{nCALL_ARGS}{tRIGHT_PAREN}{nMAYBE_BLOCK}")
        val nonBraceStatementFuncDef = getProduction("NON_BRACE_STATEMENT", "{nFUNC_DEF}$NLS")

        val nonIfNonBraceStatementAtomic = getProduction("NON_IF_NON_BRACE_STATEMENT", "{nATOMIC_STATEMENT}({tNEWLINE}|{tSEMICOLON})$NLS")
        val nonIfNonBraceStatementWhile = getProduction("NON_IF_NON_BRACE_STATEMENT", "{tWHILE}$NLS{tLEFT_PAREN}$NLS{nE_EXPR}{tRIGHT_PAREN}{nNON_IF_MAYBE_BLOCK}")
        val nonIfNonBraceStatementForEach = getProduction("NON_IF_NON_BRACE_STATEMENT", "{tFOR_EACH}$NLS{tIDENTIFIER}$NLS{tCOLON}$NLS{nTYPE}$NLS{tFROM}$NLS{tIDENTIFIER}{tLEFT_PAREN}$NLS{nCALL_ARGS}{tRIGHT_PAREN}{nNON_IF_MAYBE_BLOCK}")
        val nonIfNonBraceStatementFuncDef = getProduction("NON_IF_NON_BRACE_STATEMENT", "{nFUNC_DEF}$NLS")

        val atomicExpr = getProduction("ATOMIC_STATEMENT", "{nEXPR}")
        val atomicAssignment = getProduction("ATOMIC_STATEMENT", "{nEXPR}{tASSIGNMENT}$NLS{nEXPR}")
        val atomicBreak = getProduction("ATOMIC_STATEMENT", "{tBREAK}")
        val atomicContinue = getProduction("ATOMIC_STATEMENT", "{tCONTINUE}")
        val atomicReturnUnit = getProduction("ATOMIC_STATEMENT", "{tRETURN_UNIT}")
        val atomicReturn = getProduction("ATOMIC_STATEMENT", "{tRETURN}$NLS{nEXPR}")
        val atomicYield = getProduction("ATOMIC_STATEMENT", "{tYIELD}$NLS{nEXPR}")
        val atomicVarDef = getProduction("ATOMIC_STATEMENT", "{nVAR_DECL}")
        val atomicForeignDecl = getProduction("ATOMIC_STATEMENT", "{nFOREIGN_DECL}")

        internal fun getList(): List<Production<Symbol>> {
            return listOf(
                program,
                type, tableType, const,
                varDecl, funcDef, foreignDecl,
                defArgs1, defArgs2, defArg, callArgs1, callArgs2,

                expr4096Parenthesis, expr4096Const, expr4096Identifier, expr4096Call, expr4096ArrayDefaultAllocation, expr4096ArrayListAllocation,
                expr2048PassThrough, expr2048ArrayLength, expr2048ArrayAccess, expr2048UnaryPlus, expr2048UnaryMinus, expr2048UnaryBoolNot, expr2048UnaryBitNot,
                expr1024PassThrough, expr1024Multiply, expr1024Divide, expr1024Modulo,
                expr512PassThrough, expr512Plus, expr512Minus,
                expr256PassThrough, expr256ShiftLeft, expr256ShiftRight,
                expr128PassThrough, expr128BitAnd,
                expr64PassThrough, expr64BitXor,
                expr32PassThrough, expr32BitOr,
                expr16PassThrough, expr16Equal, expr16NotEqual, expr16LessThan, expr16LessOrEq, expr16GreaterThan, expr16GreaterOrEq,
                expr8PassThrough, expr8BoolXor, expr8BoolIff,
                expr4PassThrough, expr4BoolAnd,
                expr2PassThrough, expr2BoolOr,
                exprPassThrough, exprTernary,

                eExpr4096Parenthesis, eExpr4096Const, eExpr4096Identifier, eExpr4096Call, eExpr4096ArrayDefaultAllocation, eExpr4096ArrayListAllocation,
                eExpr2048PassThrough, eExpr2048ArrayLength, eExpr2048ArrayAccess, eExpr2048UnaryPlus, eExpr2048UnaryMinus, eExpr2048UnaryBoolNot, eExpr2048UnaryBitNot,
                eExpr1024PassThrough, eExpr1024Multiply, eExpr1024Divide, eExpr1024Modulo,
                eExpr512PassThrough, eExpr512Plus, eExpr512Minus,
                eExpr256PassThrough, eExpr256ShiftLeft, eExpr256ShiftRight,
                eExpr128PassThrough, eExpr128BitAnd,
                eExpr64PassThrough, eExpr64BitXor,
                eExpr32PassThrough, eExpr32BitOr,
                eExpr16PassThrough, eExpr16Equal, eExpr16NotEqual, eExpr16LessThan, eExpr16LessOrEq, eExpr16GreaterThan, eExpr16GreaterOrEq,
                eExpr8PassThrough, eExpr8BoolXor, eExpr8BoolIff,
                eExpr4PassThrough, eExpr4BoolAnd,
                eExpr2PassThrough, eExpr2BoolOr,
                eExprPassThrough, eExprTernary,

                manyStatements,
                maybeBlockNonBrace, maybeBlockBraces,
                nonIfMaybeBlockNonBrace, nonIfMaybeBlockBraces,
                statementNonBrace, statementBraces,
                nonBraceStatementAtomic, nonBraceStatementIf, nonBraceStatementWhile, nonBraceStatementForEach, nonBraceStatementFuncDef,
                nonIfNonBraceStatementAtomic, nonIfNonBraceStatementWhile, nonIfNonBraceStatementForEach, nonIfNonBraceStatementFuncDef,
                atomicExpr, atomicAssignment, atomicBreak, atomicContinue, atomicReturnUnit, atomicReturn, atomicYield, atomicVarDef, atomicForeignDecl
            )
        }
    }

    fun getGrammar(): Grammar<Symbol> {
        return Grammar(SymbolRegexParser.getSymbolFromString("nPROGRAM"), Productions.getList())
    }
}
