package compiler.analysis

import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.diagnostics.Diagnostics
import compiler.utils.Ref

object ProgramAnalyzer {
    data class ProgramProperties(
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>,
        val argumentResolution: Map<Ref<Expression.FunctionCall.Argument>, Ref<Function.Parameter>>,
        val expressionTypes: Map<Ref<Expression>, Type>,
        val defaultParameterMapping: Map<Ref<Function.Parameter>, Variable>,
        val functionReturnedValueVariables: Map<Ref<Function>, Variable>,
        val variableProperties: Map<Ref<AstNode>, VariablePropertiesAnalyzer.VariableProperties>,
        val generatorProperties: Map<Ref<Function>, List<Ref<Statement.ForeachLoop>>>,
        val staticDepth: Int,
    )

    fun analyzeProgram(ast: Program, diagnostics: Diagnostics): ProgramProperties {
        val nameResolutionResult = NameResolver.calculateNameResolution(ast, diagnostics)
        val nameResolution = nameResolutionResult.nameDefinitions
        val argumentResolution = ArgumentResolver.calculateArgumentToParameterResolution(ast, nameResolution, diagnostics)
        val expressionTypes = TypeChecker.calculateTypes(ast, nameResolution, argumentResolution.argumentsToParametersMap, diagnostics)
        val defaultParameterMapping = DefaultParameterResolver.mapFunctionParametersToDummyVariables(ast)
        InitializationVerifier.verifyAccessedVariablesAreInitialized(ast, nameResolution, defaultParameterMapping, diagnostics)
        val functionReturnedValueVariables = ReturnValueVariableCreator.createDummyVariablesForFunctionReturnValue(ast)
        val variableProperties = VariablePropertiesAnalyzer.calculateVariableProperties(ast, nameResolution, defaultParameterMapping, functionReturnedValueVariables, argumentResolution.accessedDefaultValues, diagnostics)
        val generatorProperties = GeneratorResolver.computeGeneratorProperties(ast)

        return ProgramProperties(
            nameResolution,
            argumentResolution.argumentsToParametersMap,
            expressionTypes,
            defaultParameterMapping,
            functionReturnedValueVariables,
            variableProperties,
            generatorProperties,
            nameResolutionResult.programStaticDepth,
        )
    }
}
