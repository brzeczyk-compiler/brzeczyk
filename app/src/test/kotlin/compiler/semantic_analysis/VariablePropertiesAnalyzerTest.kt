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
import compiler.common.diagnostics.Diagnostic.VariablePropertiesError
import compiler.common.diagnostics.Diagnostic.VariablePropertiesError.AssignmentToFunctionParameter
import compiler.common.reference_collections.MutableReferenceMap
import compiler.common.reference_collections.ReferenceHashMap
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
            input.nameResolution,
            CompilerDiagnostics()
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
        expectedResults[variable] = VariableProperties(null, mutableSetOf(), mutableSetOf())

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
        expectedResults[variable] = VariableProperties(function, mutableSetOf(), mutableSetOf())

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
    }

    // czynność zewnętrzna(x: Liczba = 123) {}

    @Test
    fun `test function parameter has an owner`() {
        val parameterX = Function.Parameter("x", Type.Number, Expression.NumberLiteral(123))
        val function = Function(
            "zewnętrzna", listOf(parameterX), Type.Unit,
            listOf()
        )
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        val input = VariablePropertyInput(Program(listOf(FunctionDefinition(function))), nameResolution)

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults[parameterX] = VariableProperties(function, mutableSetOf(), mutableSetOf())

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
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution[readFromX] = variableX
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults[variableX] = VariableProperties(outer, mutableSetOf(outer), mutableSetOf())
        expectedResults[variableY] = VariableProperties(outer, mutableSetOf(), mutableSetOf())

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
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution[assignmentToX] = variableX
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults[variableX] = VariableProperties(outer, mutableSetOf(), mutableSetOf(outer))

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
    }

    // czynność zewnętrzna(x: Liczba) {
    //     x = 124
    // }

    @Test
    fun `test write to function parameter`() {
        val parameterX = Function.Parameter("x", Type.Number, Expression.NumberLiteral(123))
        val assignmentToX = Assignment("x", Expression.NumberLiteral(124))
        val outer = Function(
            "zewnętrzna", listOf(parameterX), Type.Unit,
            listOf(assignmentToX)
        )
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution[assignmentToX] = parameterX
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults[parameterX] = VariableProperties(outer, mutableSetOf(), mutableSetOf(outer))

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(
            input,
            listOf(AssignmentToFunctionParameter(parameterX, outer, outer))
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
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution[readFromX] = variableX
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults[variableX] = VariableProperties(outer, mutableSetOf(inner), mutableSetOf())
        expectedResults[variableY] = VariableProperties(inner, mutableSetOf(), mutableSetOf())

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
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution[assignmentToX] = variableX
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults[variableX] = VariableProperties(outer, mutableSetOf(), mutableSetOf(inner))

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
        val parameterX = Function.Parameter("x", Type.Number, Expression.NumberLiteral(123))
        val assignmentToX = Assignment("x", Expression.NumberLiteral(124))
        val inner = Function(
            "wewnętrzna", listOf(), Type.Unit,
            listOf(assignmentToX)
        )
        val outer = Function(
            "zewnętrzna", listOf(parameterX), Type.Unit,
            listOf(Statement.FunctionDefinition(inner))
        )
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution[assignmentToX] = parameterX
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults[parameterX] = VariableProperties(outer, mutableSetOf(), mutableSetOf(inner))

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(
            input,
            listOf(AssignmentToFunctionParameter(parameterX, outer, inner))
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
        val outer = Function(
            "zewnętrzna", listOf(parameterX), Type.Unit,
            listOf(Statement.FunctionDefinition(inner))
        )
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution[readFromX] = parameterX
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults[parameterX] = VariableProperties(outer, mutableSetOf(inner), mutableSetOf())
        expectedResults[variableY] = VariableProperties(inner, mutableSetOf(), mutableSetOf())

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
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution[readFromX] = variableX
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults[variableX] = VariableProperties(outer, mutableSetOf(innerDeep), mutableSetOf())
        expectedResults[variableY] = VariableProperties(innerDeep, mutableSetOf(), mutableSetOf())

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
                Statement.FunctionDefinition(inner)
            )
        )
        val nameResolution: MutableReferenceMap<Any, NamedNode> = ReferenceHashMap()
        nameResolution[readFromX] = variableX
        nameResolution[assignmentToXOuter] = variableX
        nameResolution[assignmentToXInner] = variableX
        nameResolution[assignmentToXInnerDeep] = variableX
        val input = VariablePropertyInput(
            Program(listOf(FunctionDefinition(outer))),
            nameResolution
        )

        val expectedResults: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        expectedResults[variableX] = VariableProperties(
            outer,
            mutableSetOf(outer, inner, innerDeep), mutableSetOf(outer, inner, innerDeep)
        )
        expectedResults[variableYOuter] = VariableProperties(outer, mutableSetOf(), mutableSetOf())
        expectedResults[variableYInner] = VariableProperties(inner, mutableSetOf(), mutableSetOf())
        expectedResults[variableYInnerDeep] = VariableProperties(innerDeep, mutableSetOf(), mutableSetOf())

        assertAnalysisResults(input, expectedResults)
        assertDiagnostics(input, listOf())
    }
}
