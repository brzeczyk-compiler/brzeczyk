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

class ProgramAnalyzer(private val diagnostics: Diagnostics) {
    data class ProgramProperties(
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>,
        val argumentResolution: Map<Ref<Expression.FunctionCall.Argument>, Ref<Function.Parameter>>,
        val expressionTypes: Map<Ref<Expression>, Type>,
        val defaultParameterMapping: Map<Ref<Function.Parameter>, Variable>,
        val functionReturnedValueVariables: Map<Ref<Function>, Variable>,
        val variableProperties: Map<Ref<AstNode>, VariablePropertiesAnalyzer.VariableProperties>,
        val foreachLoopsInGenerators: Map<Ref<Function>, List<Ref<Statement.ForeachLoop>>>,
        val staticDepth: Int,
    )

    fun analyze(program: Program): ProgramProperties {
        val nameResolutionResult = NameResolver.calculateNameResolution(program, diagnostics)
        val nameResolution = nameResolutionResult.nameDefinitions
        val argumentResolution = ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)
        val expressionTypes = TypeChecker.calculateTypes(program, nameResolution, argumentResolution.argumentsToParametersMap, diagnostics)
        val defaultParameterMapping = DefaultParameterResolver.mapFunctionParametersToDummyVariables(program)
        InitializationVerifier.verifyAccessedVariablesAreInitialized(program, nameResolution, defaultParameterMapping, diagnostics)
        val functionReturnedValueVariables = ReturnValueVariableCreator.createDummyVariablesForFunctionReturnValue(program)
        val variableProperties = VariablePropertiesAnalyzer.calculateVariableProperties(program, nameResolution, defaultParameterMapping, functionReturnedValueVariables, argumentResolution.accessedDefaultValues, diagnostics)
        val foreachLoopsInGenerators = GeneratorAnalyzer.listForeachLoopsInGenerators(program)

        return ProgramProperties(
            nameResolution,
            argumentResolution.argumentsToParametersMap,
            expressionTypes,
            defaultParameterMapping,
            functionReturnedValueVariables,
            variableProperties,
            foreachLoopsInGenerators,
            nameResolutionResult.programStaticDepth,
        )
    }
}
