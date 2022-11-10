package compiler.semantic_analysis

import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.common.diagnostics.Diagnostics
import compiler.common.semantic_analysis.ReferenceMap
import java.lang.Exception

object NameResolver {

    sealed class NameResolutionError {
        class AssignmentToUndefinedVariable(message: String) : Exception(message)
        class UseOfUndefinedVariable(message: String) : Exception(message)
        class UseOfUndefinedFunction(message: String) : Exception(message)
    }

    fun calculateNameResolution(ast: Program, diagnostics: Diagnostics): ReferenceMap<Any, NamedNode> {
        return TODO()
    }
}
