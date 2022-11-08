package compiler.semantic_analysis

import compiler.ast.AstNodeTypes
import compiler.ast.Expression
import compiler.ast.Program
import compiler.ast.Type
import compiler.common.diagnostics.Diagnostics
import compiler.common.semantic_analysis.ReferenceHashMap
import compiler.common.semantic_analysis.ReferenceMap

object TypeChecker {
    fun calculateTypes(ast: Program, nameResolution: ReferenceMap<Any, AstNodeTypes.NamedNode>, diagnostics: Diagnostics): ReferenceHashMap<Expression, Type> {
        return TODO()
    }
}
