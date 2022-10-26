package compiler.common.diagnostics

fun interface Diagnostics {
    fun report(diagnostic: Diagnostic)
}
