package compiler.analysis

import compiler.analysis.VariablePropertiesAnalyzer.VariableProperties
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Program.Global.FunctionDefinition
import compiler.ast.Program.Global.VariableDefinition
import compiler.ast.Statement
import compiler.ast.Statement.Assignment
import compiler.ast.Type
import compiler.ast.Variable
import compiler.diagnostics.CompilerDiagnostics
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.VariablePropertiesError
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.VariablePropertiesError.AssignmentToFunctionParameter
import compiler.utils.Ref
import compiler.utils.keyRefMapOf
import compiler.utils.refMapOf
import compiler.utils.refSetOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VariablePropertiesAnalyzerTest {

    private data class VariablePropertyInput(
        val program: Program,
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>,
        val defaultParameterMapping: Map<Ref<Function.Parameter>, Variable> = keyRefMapOf(),
        val functionReturnedValueVariables: Map<Ref<Function>, Variable> = keyRefMapOf(),
        val accessedDefaultValues: Map<Ref<Expression.FunctionCall>, Set<Ref<Function.Parameter>>> = keyRefMapOf(),
    )

    private fun checkAnalysisResults(
        input: VariablePropertyInput,
        expectedAnalysisResults: Map<Ref<AstNode>, VariableProperties>
    ) {
        val actualAnalysisResults = VariablePropertiesAnalyzer.calculateVariableProperties(
            input.program,
            input.nameResolution,
            input.defaultParameterMapping,
            input.functionReturnedValueVariables,
            input.accessedDefaultValues,
            CompilerDiagnostics(),
        )
        assertEquals(expectedAnalysisResults, actualAnalysisResults)
    }

    private fun checkDiagnostics(
        input: VariablePropertyInput,
        expectedDiagnostics: List<VariablePropertiesError>,
    ) {
        val actualDiagnostics = CompilerDiagnostics()

        fun calculate() {
            VariablePropertiesAnalyzer.calculateVariableProperties(
                input.program,
                input.nameResolution,
                input.defaultParameterMapping,
                input.functionReturnedValueVariables,
                input.accessedDefaultValues,
                actualDiagnostics,
            )
        }

        if (expectedDiagnostics.isNotEmpty())
            assertFailsWith<VariablePropertiesAnalyzer.AnalysisFailed> {
                calculate()
            }
        else
            calculate()

        assertContentEquals(expectedDiagnostics, actualDiagnostics.diagnostics.filterIsInstance<VariablePropertiesError>().toList())
    }

    // zm x: Liczba = 123

    @Test
    fun `test unused variable is global`() {
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(123))
        val input = VariablePropertyInput(Program(listOf(VariableDefinition(variable))), refMapOf())

        val expectedResults = keyRefMapOf<AstNode, VariableProperties>(
            variable to VariableProperties(VariablePropertiesAnalyzer.GlobalContext, refSetOf(), refSetOf()),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba = 123
    // }

    @Test
    fun `test variable in function has a parent`() {
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(123))
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(Statement.VariableDefinition(variable))
        )
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), refMapOf())

        val expectedResults: Map<Ref<AstNode>, VariableProperties> = keyRefMapOf(
            variable to VariableProperties(function, refSetOf(), refSetOf()),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna(x: Liczba = 123) {}

    @Test
    fun `test function parameter has an owner`() {
        val parameterX = Function.Parameter("x", Type.Number, Expression.NumberLiteral(123))
        val dummyVariableX = Variable(Variable.Kind.CONSTANT, "dummy", Type.Number, Expression.NumberLiteral(123))
        val function = Function(
            "zewnętrzna", listOf(parameterX), Type.Unit,
            listOf()
        )
        val defaultParameterMapping = keyRefMapOf(parameterX to dummyVariableX)
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), refMapOf(), defaultParameterMapping)

        val expectedResults = keyRefMapOf<AstNode, VariableProperties>(
            parameterX to VariableProperties(function, refSetOf(), refSetOf()),
            dummyVariableX to VariableProperties(VariablePropertiesAnalyzer.GlobalContext, refSetOf(), refSetOf()),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna() {
    //     czynność wewnętrzna(x: Liczba = 123) { }
    // }

    @Test
    fun `test inner function default parameter`() {
        val parameterX = Function.Parameter("x", Type.Number, Expression.NumberLiteral(123))
        val dummyVariableX = Variable(Variable.Kind.VALUE, "dummy", Type.Number, Expression.NumberLiteral(123))
        val innerFunction = Function("wewnętrzna", listOf(parameterX), Type.Unit, listOf())
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(innerFunction)
            )
        )
        val defaultParameterMapping = keyRefMapOf(parameterX to dummyVariableX)
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), refMapOf(), defaultParameterMapping)

        val expectedResults: Map<Ref<AstNode>, VariableProperties> = keyRefMapOf(
            parameterX to VariableProperties(innerFunction, refSetOf(), refSetOf()),
            dummyVariableX to VariableProperties(function, refSetOf(), refSetOf(function)),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba = 123
    //     zm y: Liczba = x
    // }

    @Test
    fun `test read variable at same level`() {
        val variableX = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")
        val variableY = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val outer = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(Statement.VariableDefinition(variableX), Statement.VariableDefinition(variableY))
        )
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(readFromX to variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: Map<Ref<AstNode>, VariableProperties> = keyRefMapOf(
            variableX to VariableProperties(outer, refSetOf(outer), refSetOf()),
            variableY to VariableProperties(outer, refSetOf(), refSetOf()),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba = 123
    //     x = 124
    // }

    @Test
    fun `test write to variable at same level`() {
        val variableX = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(123))
        val assignmentToX = Assignment("x", Expression.NumberLiteral(124))
        val outer = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(Statement.VariableDefinition(variableX), assignmentToX)
        )
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(assignmentToX to variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: Map<Ref<AstNode>, VariableProperties> = keyRefMapOf(
            variableX to VariableProperties(outer, refSetOf(), refSetOf(outer)),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna(x: Liczba) {
    //     x = 124
    // }

    @Test
    fun `test write to function parameter`() {
        val parameterX = Function.Parameter("x", Type.Number, null)
        val assignmentToX = Assignment("x", Expression.NumberLiteral(124))
        val outer = Function(
            "zewnętrzna", listOf(parameterX), Type.Unit,
            listOf(assignmentToX)
        )
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(assignmentToX to parameterX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        checkDiagnostics(
            input,
            listOf(AssignmentToFunctionParameter(parameterX, outer, outer)),
        )
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba = 123
    //     czynność wewnętrzna() {
    //         zm y: Liczba = x
    //     }
    // }

    @Test
    fun `test read in inner function`() {
        val variableX = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")
        val variableY = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val inner = Function(
            "wewnętrzna", listOf(), Type.Unit,
            listOf(Statement.VariableDefinition(variableY))
        )
        val outer = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(Statement.VariableDefinition(variableX), Statement.FunctionDefinition(inner))
        )
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(readFromX to variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: Map<Ref<AstNode>, VariableProperties> = keyRefMapOf(
            variableX to VariableProperties(outer, refSetOf(inner), refSetOf()),
            variableY to VariableProperties(inner, refSetOf(), refSetOf()),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba = 123
    //     czynność wewnętrzna() {
    //         x = 124
    //     }
    // }

    @Test
    fun `test write to variable in inner function`() {
        val variableX = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(123))
        val assignmentToX = Assignment("x", Expression.NumberLiteral(124))
        val inner = Function(
            "wewnętrzna", listOf(), Type.Unit,
            listOf(assignmentToX)
        )
        val outer = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(Statement.VariableDefinition(variableX), Statement.FunctionDefinition(inner))
        )
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(assignmentToX to variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: Map<Ref<AstNode>, VariableProperties> = keyRefMapOf(
            variableX to VariableProperties(outer, refSetOf(), refSetOf(inner)),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna(x: Liczba) {
    //     czynność wewnętrzna() {
    //         x = 124
    //     }
    // }

    @Test
    fun `test write to function parameter in inner function`() {
        val parameterX = Function.Parameter("x", Type.Number, null)
        val assignmentToX = Assignment("x", Expression.NumberLiteral(124))
        val inner = Function(
            "wewnętrzna", listOf(), Type.Unit,
            listOf(assignmentToX)
        )
        val outer = Function(
            "zewnętrzna", listOf(parameterX), Type.Unit,
            listOf(Statement.FunctionDefinition(inner))
        )
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(assignmentToX to parameterX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        checkDiagnostics(
            input,
            listOf(AssignmentToFunctionParameter(parameterX, outer, inner)),
        )
    }
    // czynność zewnętrzna(x: Liczba = 123) {
    //     czynność wewnętrzna() {
    //         zm y: Liczba = x
    //     }
    // }

    @Test
    fun `test read outer parameter in inner function`() {
        val readFromX = Expression.Variable("x")
        val variableY = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val inner = Function(
            "wewnętrzna", listOf(), Type.Unit,
            listOf(
                Statement.VariableDefinition(variableY),
            )
        )
        val parameterX = Function.Parameter("x", Type.Number, Expression.NumberLiteral(123))
        val dummyVariableX = Variable(Variable.Kind.CONSTANT, "dummy", Type.Number, Expression.NumberLiteral(123))
        val outer = Function(
            "zewnętrzna", listOf(parameterX), Type.Unit,
            listOf(Statement.FunctionDefinition(inner))
        )
        val defaultParameterMapping = keyRefMapOf(parameterX to dummyVariableX)
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(readFromX to parameterX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
            defaultParameterMapping,
        )

        val expectedResults = keyRefMapOf<AstNode, VariableProperties>(
            parameterX to VariableProperties(outer, refSetOf(inner), refSetOf()),
            dummyVariableX to VariableProperties(VariablePropertiesAnalyzer.GlobalContext, refSetOf(), refSetOf()),
            variableY to VariableProperties(inner, refSetOf(), refSetOf()),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba = 123
    //     czynność wewnętrzna() {
    //         czynność wewnętrzna_zagnieżdżona() {
    //             zm y: Liczba = x
    //         }
    //     }
    // }

    @Test
    fun `test deep inner function`() {
        val variableX = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")
        val variableY = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val innerDeep = Function(
            "wewnętrzna_zagnieżdżona", listOf(), Type.Unit,
            listOf(Statement.VariableDefinition(variableY))
        )
        val inner = Function("wewnętrzna", listOf(), Type.Unit, listOf(Statement.FunctionDefinition(innerDeep)))
        val outer = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(Statement.VariableDefinition(variableX), Statement.FunctionDefinition(inner))
        )
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(readFromX to variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: Map<Ref<AstNode>, VariableProperties> = keyRefMapOf(
            variableX to VariableProperties(outer, refSetOf(innerDeep), refSetOf()),
            variableY to VariableProperties(innerDeep, refSetOf(), refSetOf()),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba = 123
    //     x = 122
    //     zm y: Liczba = x
    //     czynność wewnętrzna() {
    //         x = 124
    //         zm y: Liczba = x
    //         czynność wewnętrzna_zagnieżdżona() {
    //             x = 125
    //             zm y: Liczba = x
    //         }
    //     }
    // }

    @Test
    fun `test perform operations of different kind at multiple levels`() {
        val variableX = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")
        val variableYOuter = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val variableYInner = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val variableYInnerDeep = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val assignmentToXOuter = Assignment("x", Expression.NumberLiteral(122))
        val assignmentToXInner = Assignment("x", Expression.NumberLiteral(124))
        val assignmentToXInnerDeep = Assignment("x", Expression.NumberLiteral(125))
        val innerDeep = Function(
            "wewnętrzna_zagnieżdżona", listOf(), Type.Unit,
            listOf(assignmentToXInnerDeep, Statement.VariableDefinition(variableYInnerDeep))
        )
        val inner = Function(
            "wewnętrzna", listOf(), Type.Unit,
            listOf(
                assignmentToXInner, Statement.VariableDefinition(variableYInner),
                Statement.FunctionDefinition(innerDeep)
            )
        )
        val outer = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(
                Statement.VariableDefinition(variableX), assignmentToXOuter, Statement.VariableDefinition(variableYOuter),
                Statement.FunctionDefinition(inner),
            )
        )
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            readFromX to variableX,
            assignmentToXOuter to variableX,
            assignmentToXInner to variableX,
            assignmentToXInnerDeep to variableX,
        )
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: Map<Ref<AstNode>, VariableProperties> = keyRefMapOf(
            variableX to VariableProperties(outer, refSetOf(outer, inner, innerDeep), refSetOf(outer, inner, innerDeep)),
            variableYOuter to VariableProperties(outer, refSetOf(), refSetOf()),
            variableYInner to VariableProperties(inner, refSetOf(), refSetOf()),
            variableYInnerDeep to VariableProperties(innerDeep, refSetOf(), refSetOf()),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna() {
    //     czynność wewnętrzna(x: Liczba = 123) { }
    //     wewnętrzna(124)
    // }

    @Test
    fun `test unreferenced default parameter`() {
        val parameterX = Function.Parameter("x", Type.Number, Expression.NumberLiteral(123))
        val dummyVariableX = Variable(Variable.Kind.VALUE, "dummy", Type.Number, Expression.NumberLiteral(123))
        val innerFunction = Function("wewnętrzna", listOf(parameterX), Type.Unit, listOf())
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf(Expression.FunctionCall.Argument("x", Expression.NumberLiteral(124))))
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(innerFunction),
                Statement.Evaluation(innerFunctionCall),
            )
        )
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(innerFunctionCall to innerFunction)
        val defaultParameterMapping = keyRefMapOf(parameterX to dummyVariableX)
        val functionReturnedValueVariables = keyRefMapOf<Function, Variable>()
        val accessedDefaultValues: Map<Ref<Expression.FunctionCall>, Set<Ref<Function.Parameter>>> = keyRefMapOf(innerFunctionCall to refSetOf())
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), nameResolution, defaultParameterMapping, functionReturnedValueVariables, accessedDefaultValues)

        val expectedResults: Map<Ref<AstNode>, VariableProperties> = keyRefMapOf(
            parameterX to VariableProperties(innerFunction, refSetOf(), refSetOf()),
            dummyVariableX to VariableProperties(function, refSetOf(), refSetOf(function)),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }

    // czynność zewnętrzna() {
    //     czynność wewnętrzna(x: Liczba = 123) { }
    //
    //     czynność f() {
    //          wewnętrzna()
    //     }
    //     czynność g() {
    //          wewnętrzna(124)
    //     }
    // }

    @Test
    fun `test referenced default parameter`() {
        val parameterX = Function.Parameter("x", Type.Number, Expression.NumberLiteral(123))
        val dummyVariableX = Variable(Variable.Kind.VALUE, "dummy", Type.Number, Expression.NumberLiteral(123))
        val innerFunction = Function("wewnętrzna", listOf(parameterX), Type.Unit, listOf())
        val fInnerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())
        val gInnerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf(Expression.FunctionCall.Argument("x", Expression.NumberLiteral(124))))
        val fFunction = Function("f", listOf(), Type.Unit, listOf(Statement.Evaluation(fInnerFunctionCall)))
        val gFunction = Function("g", listOf(), Type.Unit, listOf(Statement.Evaluation(gInnerFunctionCall)))
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(innerFunction),
                Statement.FunctionDefinition(fFunction),
                Statement.FunctionDefinition(gFunction),
            )
        )
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            fInnerFunctionCall to innerFunction,
            gInnerFunctionCall to innerFunction,
        )
        val defaultParameterMapping = keyRefMapOf(parameterX to dummyVariableX)
        val functionReturnedValueVariables = keyRefMapOf<Function, Variable>()
        val accessedDefaultValues: Map<Ref<Expression.FunctionCall>, Set<Ref<Function.Parameter>>> = keyRefMapOf(fInnerFunctionCall to refSetOf(parameterX), gInnerFunctionCall to refSetOf())
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), nameResolution, defaultParameterMapping, functionReturnedValueVariables, accessedDefaultValues)

        val expectedResults: Map<Ref<AstNode>, VariableProperties> = keyRefMapOf(
            parameterX to VariableProperties(innerFunction, refSetOf(), refSetOf()),
            dummyVariableX to VariableProperties(function, refSetOf(fFunction), refSetOf(function)),
        )

        checkAnalysisResults(input, expectedResults)
        checkDiagnostics(input, listOf())
    }
}
