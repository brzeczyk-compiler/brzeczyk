package compiler.common.diagnostics

class CompilerDiagnostics : Diagnostics {
    private val diagnosticsList: MutableList<Diagnostic> = ArrayList()
    val diagnostics get() = this.diagnosticsList.asSequence()

    override fun report(diagnostic: Diagnostic) {
        diagnosticsList.add(diagnostic)
    }
}
