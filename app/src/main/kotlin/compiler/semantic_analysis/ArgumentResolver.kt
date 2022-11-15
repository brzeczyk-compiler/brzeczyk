package compiler.semantic_analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.common.diagnostics.Diagnostics
import compiler.common.semantic_analysis.ReferenceMap

object ArgumentResolver {
    fun calculateArgumentToParameterResolution(
        ast: Program,
        diagnostics: Diagnostics
    ): ReferenceMap<Expression.FunctionCall.Argument, Function.Parameter> {
        return TODO() // also add errors under Diagnostic.ArgumentResolutionErrors
    }
}
