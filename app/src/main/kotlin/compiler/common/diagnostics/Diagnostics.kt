package compiler.common.diagnostics

interface Diagnostics {
    fun report(diagnostic: Diagnostic)
    fun hasAnyError(): Boolean
}
