package compiler.common.diagnostics

import compiler.lexer.Location

sealed class Diagnostic {
    abstract fun isError(): Boolean

    data class LexerError(val start: Location, val end: Location?, val context: List<String>, val errorSegment: String) : Diagnostic() {

        override fun isError() = true

        override fun toString() = StringBuilder()
            .append("Unable to match token at location $start - ${end ?: "eof"}.\n")
            .append("\t\t${context.joinToString("")}-->$errorSegment<---")
            .toString()
    }

    class ParserError(
        val symbol: Any?,
        val start: Location,
        val end: Location,
        val expectedSymbols: List<Any>
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

    sealed class NameResolutionErrors() : Diagnostic() {
        override fun isError() = true

        class UndefinedVariable : NameResolutionErrors()
        class UndefinedFunction : NameResolutionErrors()
        class NameConflict : NameResolutionErrors()
        class VariableIsNotCallable : NameResolutionErrors()
        class FunctionIsNotVariable : NameResolutionErrors()
    }
}
