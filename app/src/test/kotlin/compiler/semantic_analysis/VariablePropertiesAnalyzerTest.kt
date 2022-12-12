package compiler.semantic_analysis

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
import compiler.common.diagnostics.CompilerDiagnostics
import compiler.common.diagnostics.Diagnostic.ResolutionDiagnostic.VariablePropertiesError
import compiler.common.diagnostics.Diagnostic.ResolutionDiagnostic.VariablePropertiesError.AssignmentToFunctionParameter
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.ReferenceSet
import compiler.common.reference_collections.referenceHashMapOf
import compiler.common.reference_collections.referenceHashSetOf
import compiler.semantic_analysis.VariablePropertiesAnalyzer.VariableProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VariablePropertiesAnalyzerTest {

    private data class VariablePropertyInput(
        val program: Program,
        val nameResolution: ReferenceMap<Any, NamedNode>,
        val defaultParameterMapping: ReferenceMap<Function.Parameter, Variable> = referenceHashMapOf(),
        val accessedDefaultValues: ReferenceMap<Expression.FunctionCall, ReferenceSet<Function.Parameter>> = referenceHashMapOf(),
    )

    private fun assertAnalysisResults(
        input: VariablePropertyInput,
        expectedAnalysisResults: ReferenceMap<Any, VariableProperties>
    ) {
        val actualAnalysisResults = VariablePropertiesAnalyzer.calculateVariableProperties(
            input.program,
            input.nameResolution,
            input.defaultParameterMapping,
            input.accessedDefaultValues,
            CompilerDiagnostics(),
        )
        assertEquals(expectedAnalysisResults, actualAnalysisResults)
    }

    private fun assertDiagnostics(
        input: VariablePropertyInput,
        expectedDiagnostics: List<VariablePropertiesError>,
    ) {
        val actualDiagnostics = CompilerDiagnostics()

        fun calculate() {
            VariablePropertiesAnalyzer.calculateVariableProperties(
                input.program,
                input.nameResolution,
                input.defaultParameterMapping,
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

        assertResolutionDiagnosticEquals(expectedDiagnostics, actualDiagnostics.diagnostics.filter { it is VariablePropertiesError }.toList())
    }

    // zm x: Liczba = 123

    @Test
    fun `test unused variable is global`() {
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(123))
        val input = VariablePropertyInput(Program(listOf(VariableDefinition(variable))), referenceHashMapOf())

        val expectedResults = referenceHashMapOf<Any, VariableProperties>(
            variable to VariableProperties(VariablePropertiesAnalyzer.GlobalContext, referenceHashSetOf(), referenceHashSetOf()),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), referenceHashMapOf())

        val expectedResults: ReferenceMap<Any, VariableProperties> = referenceHashMapOf(
            variable to VariableProperties(function, referenceHashSetOf(), referenceHashSetOf()),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val defaultParameterMapping = referenceHashMapOf(parameterX to dummyVariableX)
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), referenceHashMapOf(), defaultParameterMapping)

        val expectedResults = referenceHashMapOf<Any, VariableProperties>(
            parameterX to VariableProperties(function, referenceHashSetOf(), referenceHashSetOf()),
            dummyVariableX to VariableProperties(VariablePropertiesAnalyzer.GlobalContext, referenceHashSetOf(), referenceHashSetOf()),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val defaultParameterMapping = referenceHashMapOf(parameterX to dummyVariableX)
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), referenceHashMapOf(), defaultParameterMapping)

        val expectedResults: ReferenceMap<Any, VariableProperties> = referenceHashMapOf(
            parameterX to VariableProperties(innerFunction, referenceHashSetOf(), referenceHashSetOf()),
            dummyVariableX to VariableProperties(function, referenceHashSetOf(), referenceHashSetOf(function)),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceHashMapOf(readFromX to variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: ReferenceMap<Any, VariableProperties> = referenceHashMapOf(
            variableX to VariableProperties(outer, referenceHashSetOf(outer), referenceHashSetOf()),
            variableY to VariableProperties(outer, referenceHashSetOf(), referenceHashSetOf()),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceHashMapOf(assignmentToX to variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: ReferenceMap<Any, VariableProperties> = referenceHashMapOf(
            variableX to VariableProperties(outer, referenceHashSetOf(), referenceHashSetOf(outer)),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceHashMapOf(assignmentToX to parameterX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        assertDiagnostics(
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
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceHashMapOf(readFromX to variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: ReferenceMap<Any, VariableProperties> = referenceHashMapOf(
            variableX to VariableProperties(outer, referenceHashSetOf(inner), referenceHashSetOf()),
            variableY to VariableProperties(inner, referenceHashSetOf(), referenceHashSetOf()),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceHashMapOf(assignmentToX to variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: ReferenceMap<Any, VariableProperties> = referenceHashMapOf(
            variableX to VariableProperties(outer, referenceHashSetOf(), referenceHashSetOf(inner)),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceHashMapOf(assignmentToX to parameterX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        assertDiagnostics(
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
        val defaultParameterMapping = referenceHashMapOf(parameterX to dummyVariableX)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceHashMapOf(readFromX to parameterX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
            defaultParameterMapping,
        )

        val expectedResults = referenceHashMapOf<Any, VariableProperties>(
            parameterX to VariableProperties(outer, referenceHashSetOf(inner), referenceHashSetOf()),
            dummyVariableX to VariableProperties(VariablePropertiesAnalyzer.GlobalContext, referenceHashSetOf(), referenceHashSetOf()),
            variableY to VariableProperties(inner, referenceHashSetOf(), referenceHashSetOf()),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceHashMapOf(readFromX to variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: ReferenceMap<Any, VariableProperties> = referenceHashMapOf(
            variableX to VariableProperties(outer, referenceHashSetOf(innerDeep), referenceHashSetOf()),
            variableY to VariableProperties(innerDeep, referenceHashSetOf(), referenceHashSetOf()),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceHashMapOf(
            readFromX to variableX,
            assignmentToXOuter to variableX,
            assignmentToXInner to variableX,
            assignmentToXInnerDeep to variableX,
        )
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution,
        )

        val expectedResults: ReferenceMap<Any, VariableProperties> = referenceHashMapOf(
            variableX to VariableProperties(outer, referenceHashSetOf(outer, inner, innerDeep), referenceHashSetOf(outer, inner, innerDeep)),
            variableYOuter to VariableProperties(outer, referenceHashSetOf(), referenceHashSetOf()),
            variableYInner to VariableProperties(inner, referenceHashSetOf(), referenceHashSetOf()),
            variableYInnerDeep to VariableProperties(innerDeep, referenceHashSetOf(), referenceHashSetOf()),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceHashMapOf(innerFunctionCall to innerFunction)
        val defaultParameterMapping = referenceHashMapOf(parameterX to dummyVariableX)
        val accessedDefaultValues: ReferenceMap<Expression.FunctionCall, ReferenceSet<Function.Parameter>> = referenceHashMapOf(innerFunctionCall to referenceHashSetOf<Function.Parameter>())
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), nameResolution, defaultParameterMapping, accessedDefaultValues)

        val expectedResults: ReferenceMap<Any, VariableProperties> = referenceHashMapOf(
            parameterX to VariableProperties(innerFunction, referenceHashSetOf(), referenceHashSetOf()),
            dummyVariableX to VariableProperties(function, referenceHashSetOf(), referenceHashSetOf(function)),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceHashMapOf(
            fInnerFunctionCall to innerFunction,
            gInnerFunctionCall to innerFunction,
        )
        val defaultParameterMapping = referenceHashMapOf(parameterX to dummyVariableX)
        val accessedDefaultValues: ReferenceMap<Expression.FunctionCall, ReferenceSet<Function.Parameter>> = referenceHashMapOf(fInnerFunctionCall to referenceHashSetOf(parameterX), gInnerFunctionCall to referenceHashSetOf())
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), nameResolution, defaultParameterMapping, accessedDefaultValues)

        val expectedResults: ReferenceMap<Any, VariableProperties> = referenceHashMapOf(
            parameterX to VariableProperties(innerFunction, referenceHashSetOf(), referenceHashSetOf()),
            dummyVariableX to VariableProperties(function, referenceHashSetOf(fFunction), referenceHashSetOf(function)),
        )

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
    }
}
