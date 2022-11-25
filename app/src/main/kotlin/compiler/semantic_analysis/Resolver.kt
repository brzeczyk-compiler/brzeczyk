package compiler.semantic_analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostics
import compiler.common.reference_collections.ReferenceMap

object Resolver {
    data class ProgramProperties(
        val nameResolution: ReferenceMap<Any, NamedNode>,
        val argumentResolution: ReferenceMap<Expression.FunctionCall.Argument, Function.Parameter>,
        val expressionTypes: ReferenceMap<Expression, Type>,
        val variableProperties: ReferenceMap<Any, VariablePropertiesAnalyzer.VariableProperties>,
        val defaultParameterMapping: ReferenceMap<Function.Parameter, Variable>,
    )

    fun resolveProgram(ast: Program, diagnostics: Diagnostics): ProgramProperties {
        val initialNameResolution = NameResolver.calculateNameResolution(ast, diagnostics)
        val argumentResolution = ArgumentResolver.calculateArgumentToParameterResolution(ast, initialNameResolution, diagnostics)
        val expressionTypes = TypeChecker.calculateTypes(ast, initialNameResolution, argumentResolution, diagnostics)
        val variableProperties = VariablePropertiesAnalyzer.calculateVariableProperties(ast, initialNameResolution, diagnostics)
        val defaultParameterResolutionResult = DefaultParameterResolver.resolveDefaultParameters(ast, initialNameResolution)

        return ProgramProperties(
            defaultParameterResolutionResult.updatedNameResolution,
            argumentResolution,
            expressionTypes,
            variableProperties,
            defaultParameterResolutionResult.defaultParameterMapping,
        )
    }
}
