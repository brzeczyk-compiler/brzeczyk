package compiler.analysis

import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Type
import compiler.ast.Variable
import compiler.diagnostics.Diagnostics
import compiler.utils.KeyRefMap
import compiler.utils.RefMap

object ProgramAnalyzer {
    data class ProgramProperties(
        val nameResolution: RefMap<AstNode, NamedNode>,
        val argumentResolution: RefMap<Expression.FunctionCall.Argument, Function.Parameter>,
        val expressionTypes: KeyRefMap<Expression, Type>,
        val defaultParameterMapping: KeyRefMap<Function.Parameter, Variable>,
        val functionReturnedValueVariables: KeyRefMap<Function, Variable>,
        val variableProperties: KeyRefMap<AstNode, VariablePropertiesAnalyzer.VariableProperties>,
        val staticDepth: Int,
    )

    fun analyzeProgram(ast: Program, diagnostics: Diagnostics): ProgramProperties {
        val nameResolutionResult = NameResolver.calculateNameResolution(ast, diagnostics)
        val nameResolution = nameResolutionResult.nameDefinitions
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
            nameResolutionResult.programStaticDepth,
        )
    }
}
