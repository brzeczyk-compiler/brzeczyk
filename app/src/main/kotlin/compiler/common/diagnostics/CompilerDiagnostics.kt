package compiler.common.diagnostics

class CompilerDiagnostics : Diagnostics {
    private val diagnosticsList: MutableList<Diagnostic> = ArrayList()
    val diagnostics get() = this.diagnosticsList.asSequence()
    override fun report(diagnostic: Diagnostic) {
        diagnosticsList.add(diagnostic)
    }

    fun clear() {
        diagnosticsList.clear()
    }

    fun hasErrors() = diagnosticsList.any { it.isError() }

    override fun toString() =
        if (diagnosticsList.size > 0) diagnostics.map { it.toString() }.joinToString(separator = "\n")
        else "OK\n"
}
