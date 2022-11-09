package compiler.semantic_analysis

import compiler.ast.Expression
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Type
import compiler.common.diagnostics.Diagnostics
import compiler.common.semantic_analysis.ReferenceMap

object TypeChecker {
    fun calculateTypes(ast: Program, nameResolution: ReferenceMap<Any, NamedNode>, diagnostics: Diagnostics): ReferenceMap<Expression, Type> {
        return TODO()
    }
}
