package compiler.semantic_analysis

import compiler.ast.AstNodeTypes
import compiler.ast.Program
import compiler.common.diagnostics.Diagnostics
import compiler.common.semantic_analysis.ReferenceHashMap
import compiler.common.semantic_analysis.ReferenceMap

object VariablePropertiesAnalyzer {
    data class VariableProperties(val owner: AstNodeTypes.FunctionDefinition, val usedInNested: Boolean)

    fun calculateVariablesProperties(ast: Program, nameResolution: ReferenceMap<Any, AstNodeTypes.NamedNode>, diagnostics: Diagnostics): ReferenceHashMap<Any, VariableProperties> {
        return TODO()
    }
}
