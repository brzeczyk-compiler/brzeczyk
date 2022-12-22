package compiler.diagnostics

interface Diagnostics {
    fun report(diagnostic: Diagnostic)
    fun hasAnyError(): Boolean
}
