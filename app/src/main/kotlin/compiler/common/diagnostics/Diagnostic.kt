package compiler.common.diagnostics

import compiler.lexer.Location
import java.lang.Integer.min

sealed class Diagnostic {
    abstract fun isError(): Boolean

    class LexerError(val start: Location, val end: Location?, val context: List<String>, val errorSegment: String) : Diagnostic() {

        private val ERROR_DISPLAY_LEN = 15

        override fun isError(): Boolean = true

        override fun toString() = StringBuilder()
                .append("Unable to match token at location $start - ${end ?: "eof"}.\n")
                .append("\t\t${context.joinToString("")}-->")
                .append(errorSegment.substring(0, min(ERROR_DISPLAY_LEN, errorSegment.length)))
                .append("<--")
        .toString()
    }
}
