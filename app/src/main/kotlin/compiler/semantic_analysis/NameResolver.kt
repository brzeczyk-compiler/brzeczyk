package compiler.semantic_analysis

import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.common.diagnostics.Diagnostics
import compiler.common.semantic_analysis.ReferenceMap

object NameResolver {
    fun calculateNameResolution(ast: Program, diagnostics: Diagnostics): ReferenceMap<Any, NamedNode> {
        return TODO()
    }
}
