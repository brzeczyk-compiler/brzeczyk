package compiler.semantic_analysis

import compiler.ast.AstNodeTypes
import compiler.ast.Program
import compiler.common.diagnostics.Diagnostics
import compiler.common.semantic_analysis.ReferenceHashMap

object NameResolver {
    fun calculateNameResolution(ast: Program, diagnostics: Diagnostics): ReferenceHashMap<Any, AstNodeTypes.NamedNode> {
        return TODO()
    }
}
