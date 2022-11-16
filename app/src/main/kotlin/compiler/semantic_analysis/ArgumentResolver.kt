package compiler.semantic_analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.common.diagnostics.Diagnostics
import compiler.common.reference_collections.ReferenceMap

typealias ArgumentResolutionResult = ReferenceMap<Expression.FunctionCall.Argument, Function.Parameter>

object ArgumentResolver {
    fun calculateArgumentToParameterResolution(
        ast: Program,
        diagnostics: Diagnostics
    ): ArgumentResolutionResult {
        return TODO() // also add errors under Diagnostic.ArgumentResolutionError and uncomment in Compiler
    }
}
