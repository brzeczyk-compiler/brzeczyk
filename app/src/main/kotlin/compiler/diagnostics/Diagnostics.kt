package compiler.diagnostics

interface Diagnostics {
    fun report(diagnostic: Diagnostic)
    fun hasAnyErrors(): Boolean
}
