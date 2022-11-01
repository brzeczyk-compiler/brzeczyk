package compiler.common.diagnostics

import compiler.lexer.Location

sealed class Diagnostic {
    abstract fun isError(): Boolean

    data class LexerError(val start: Location, val end: Location?, val context: List<String>, val errorSegment: String) : Diagnostic() {

        override fun isError(): Boolean = true

        override fun toString() = StringBuilder()
            .append("Unable to match token at location $start - ${end ?: "eof"}.\n")
            .append("\t\t${context.joinToString("")}-->$errorSegment<---")
            .toString()
    }
}
