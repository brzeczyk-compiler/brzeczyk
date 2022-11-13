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
import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostic.VariablePropertiesError
import compiler.common.semantic_analysis.MutableReferenceMap
import compiler.common.semantic_analysis.ReferenceHashMap
import compiler.semantic_analysis.VariablePropertiesAnalyzer.VariableProperties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class VariablePropertiesAnalyzerTest {

    private data class VariablePropertyInput(
        val program: Program,
        val nameResolution: MutableReferenceMap<Any, NamedNode>
    )

    private fun assertAnalysisResults(
        input: VariablePropertyInput,
        expectedAnalysisResults: MutableReferenceMap<Any, VariableProperties>
    ) {
        val actualAnalysisResults = VariablePropertiesAnalyzer.calculateVariableProperties(
            input.program,
            input.nameResolution, CompilerDiagnostics()
        )
        assertEquals(expectedAnalysisResults, actualAnalysisResults)
    }

    private fun assertDiagnostics(
        input: VariablePropertyInput,
        expectedDiagnostics: List<VariablePropertiesError>
    ) {
        val actualDiagnostics = CompilerDiagnostics()
        VariablePropertiesAnalyzer.calculateVariableProperties(
            input.program,
            input.nameResolution, actualDiagnostics
        )
        assertContentEquals(
            expectedDiagnostics.asSequence(),
            actualDiagnostics.diagnostics.filter { it is VariablePropertiesError }
        )
    }

    // zm x: Liczba = 123

    @Test
    fun `test unused variable has no parent`() {
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(123))
        val input = VariablePropertyInput(Program(listOf(VariableDefinition(variable))), ReferenceHashMap())

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults.put(variable, VariableProperties(null, false))

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
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), ReferenceHashMap())

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults.put(variable, VariableProperties(function, false))

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
    }

    // czynność zewnętrzna(x: Liczba = 123) {}
    @Test
    fun `test function parameter has a parent`() {
        val parameterX = Function.Parameter("x", Type.Number, Expression.NumberLiteral(123))
        val function = Function(
            "zewnętrzna", listOf(parameterX), Type.Unit,
            listOf()
        )
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), nameResolution)

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults.put(parameterX, VariableProperties(function, false))

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution.put(readFromX, variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults.put(variableX, VariableProperties(outer, true))
        expectedResults.put(variableY, VariableProperties(inner, false))

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
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
        val outer = Function(
            "zewnętrzna", listOf(parameterX), Type.Unit,
            listOf(Statement.FunctionDefinition(inner))
        )
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution.put(readFromX, parameterX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults.put(parameterX, VariableProperties(outer, true))
        expectedResults.put(variableY, VariableProperties(inner, false))

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
    fun `test read in deep inner function`() {
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
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution.put(readFromX, variableX)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults.put(variableX, VariableProperties(outer, true))
        expectedResults.put(variableY, VariableProperties(innerDeep, false))

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
    fun `test assignment to outer variable generates an error`() {
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(123))
        val assignmentToX = Assignment("x", Expression.NumberLiteral(124))
        val inner = Function("wewnętrzna", listOf(), Type.Unit, listOf(assignmentToX))
        val outer = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(Statement.VariableDefinition(variable), Statement.FunctionDefinition(inner))
        )
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution.put(assignmentToX, variable)
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults.put(variable, VariableProperties(outer, true))

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(
            input,
            listOf(
                Diagnostic.VariablePropertiesError.AssignmentToOuterVariable(variable, outer, inner)
            )
        )
    }
    // czynność zewnętrzna(x: Liczba = 123) {
    //     x = 124
    // }
    @Test
    fun `test assignment to function parameter generates an error`() {
        val parameterX = Function.Parameter("x", Type.Number, Expression.NumberLiteral(123))
        val assignmentToX = Assignment("x", Expression.NumberLiteral(124))
        val function = Function(
            "zewnętrzna", listOf(parameterX), Type.Unit,
            listOf(assignmentToX)
        )
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution.put(assignmentToX, parameterX)
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), nameResolution)

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults.put(parameterX, VariableProperties(function, false))

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(
            input,
            listOf(
                Diagnostic.VariablePropertiesError.AssignmentToFunctionParameter(parameterX, function, function)
            )
        )
    }
}
