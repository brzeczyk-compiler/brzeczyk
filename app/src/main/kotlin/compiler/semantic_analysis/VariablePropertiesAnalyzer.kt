package compiler.semantic_analysis

import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.common.diagnostics.Diagnostics
import compiler.common.semantic_analysis.ReferenceMap

object VariablePropertiesAnalyzer {
    data class VariableProperties(val owner: Function, val usedInNested: Boolean)

    fun calculateVariableProperties(ast: Program, nameResolution: ReferenceMap<Any, NamedNode>, diagnostics: Diagnostics): ReferenceMap<Any, VariableProperties> {
        return TODO()
    }
}
