package compiler.common.diagnostics

sealed class Diagnostic {
    abstract fun isError(): Boolean

    class LexerError : Diagnostic() {
        override fun isError(): Boolean {
            TODO("Not yet implemented")
        }
    }
}
