package compiler.common.diagnostics

import compiler.lexer.Location

sealed class Diagnostic {
    abstract fun isError(): Boolean

    class LexerError : Diagnostic() {
        override fun isError(): Boolean {
            TODO("Not yet implemented")
        }
    }

    class ParserError(
        val symbol: String?,
        val start: Location,
        val end: Location,
        val expectedSymbols: List<String>
    ) : Diagnostic() {
        override fun isError() = true

        override fun toString() = StringBuilder().apply {
            if (symbol != null)
                append("Unexpected symbol $symbol at location $start - $end.")
            else
                append("Unexpected end of file.")
            if (expectedSymbols.isNotEmpty())
                append(" Expected symbols: ${expectedSymbols.joinToString()}.")
        }.toString()
    }
}
