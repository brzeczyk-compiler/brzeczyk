package compiler.analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Type
import compiler.ast.Variable
import compiler.diagnostics.Diagnostics
import compiler.utils.ReferenceMap

object ProgramAnalyzer {
    data class ProgramProperties(
        val nameResolution: ReferenceMap<Any, NamedNode>,
        val argumentResolution: ReferenceMap<Expression.FunctionCall.Argument, Function.Parameter>,
        val expressionTypes: ReferenceMap<Expression, Type>,
        val defaultParameterMapping: ReferenceMap<Function.Parameter, Variable>,
        val functionReturnedValueVariables: ReferenceMap<Function, Variable>,
        val variableProperties: ReferenceMap<Any, VariablePropertiesAnalyzer.VariableProperties>,
    )

    fun analyzeProgram(ast: Program, diagnostics: Diagnostics): ProgramProperties {
        val nameResolution = NameResolver.calculateNameResolution(ast, diagnostics)
        val argumentResolution = ArgumentResolver.calculateArgumentToParameterResolution(ast, nameResolution, diagnostics)
        val expressionTypes = TypeChecker.calculateTypes(ast, nameResolution, argumentResolution.argumentsToParametersMap, diagnostics)
        val defaultParameterMapping = DefaultParameterResolver.mapFunctionParametersToDummyVariables(ast)
        val functionReturnedValueVariables = ReturnValueVariableCreator.createDummyVariablesForFunctionReturnValue(ast)
        val variableProperties = VariablePropertiesAnalyzer.calculateVariableProperties(ast, nameResolution, defaultParameterMapping, functionReturnedValueVariables, argumentResolution.accessedDefaultValues, diagnostics)

        return ProgramProperties(
            nameResolution,
            argumentResolution.argumentsToParametersMap,
            expressionTypes,
            defaultParameterMapping,
            functionReturnedValueVariables,
            variableProperties,
        )
    }
}
