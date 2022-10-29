package compiler.common.diagnostics

sealed class Diagnostic {
    abstract fun isError(): Boolean

    class LexerError : Diagnostic() {
        override fun isError(): Boolean {
            TODO("Not yet implemented")
        }
    }

    class ParserError(
        val symbol: Any?,
        val start: Any?,
        val end: Any?,
        val expectedSymbols: List<Any>
    ) : Diagnostic() {
        override fun isError() = true

        override fun toString() = StringBuilder().apply {
            if (symbol != null)
                append("Unexpected symbol $symbol at location ${start ?: "?"} - ${end ?: "?"}.")
            else
                append("Unexpected end of file.")
            if (expectedSymbols.isNotEmpty())
                append(" Expected symbols: ${expectedSymbols.joinToString()}.")
        }.toString()
    }
}
