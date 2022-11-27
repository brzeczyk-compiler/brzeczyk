package compiler.semantic_analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.referenceEntries
import compiler.common.reference_collections.referenceKeys
import compiler.common.reference_collections.referenceMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class DefaultParameterResolverTest {

    private fun assertSimilarVariables(expected: Variable, actual: Variable) {
        assertEquals(expected.kind, actual.kind)
        assertEquals(expected.type, actual.type)
        assertEquals(expected.value, actual.value)
    }

    private fun compareMappings(
        expectedMappingSimplified: ReferenceMap<Function.Parameter, Variable>,
        actualMapping: ReferenceMap<Function.Parameter, Variable>
    ) {
        assertEquals(expectedMappingSimplified.referenceEntries.size, actualMapping.referenceEntries.size)
        actualMapping.referenceEntries.forEach {
            assertTrue(it.key in expectedMappingSimplified.referenceKeys)
            assertSimilarVariables(expectedMappingSimplified[it.key]!!, it.value)
        }
    }

    @Test fun `test default parameters mapping in a global function`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność test(
            a: Liczba,
            b: Czy,
            c: Liczba = 17,
            d: Czy = prawda
        ) { }

        */

        val aParameter = Function.Parameter("a", Type.Number, null)
        val bParameter = Function.Parameter("b", Type.Boolean, null)

        val cValue = Expression.NumberLiteral(17)
        val cParameter = Function.Parameter("c", Type.Number, cValue)
        val dValue = Expression.BooleanLiteral(true)
        val dParameter = Function.Parameter("d", Type.Boolean, dValue)

        val testFunction = Function(
            "test",
            listOf(
                aParameter,
                bParameter,
                cParameter,
                dParameter,
            ),
            Type.Unit, listOf()
        )
        val globals = listOf(
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val actualMapping = DefaultParameterResolver.mapFunctionParametersToDummyVariables(program)

        val expectedMappingSimplified = referenceMapOf(
            cParameter to Variable(Variable.Kind.CONSTANT, "test", Type.Number, cValue),
            dParameter to Variable(Variable.Kind.CONSTANT, "test", Type.Boolean, dValue),
        )

        compareMappings(expectedMappingSimplified, actualMapping)
    }

    @Test fun `test default parameters mapping in a local function`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność test() {
            czynność f(
                a: Liczba,
                b: Czy,
                c: Liczba = 17,
                d: Czy = prawda
            ) { }
        }

        */

        val aParameter = Function.Parameter("a", Type.Number, null)
        val bParameter = Function.Parameter("b", Type.Boolean, null)

        val cValue = Expression.NumberLiteral(17)
        val cParameter = Function.Parameter("c", Type.Number, cValue)
        val dValue = Expression.BooleanLiteral(true)
        val dParameter = Function.Parameter("d", Type.Boolean, dValue)

        val fFunction = Function(
            "f",
            listOf(
                aParameter,
                bParameter,
                cParameter,
                dParameter,
            ),
            Type.Unit, listOf()
        )
        val testFunction = Function("test", listOf(), Type.Unit, listOf(Statement.FunctionDefinition(fFunction)))
        val globals = listOf(
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val actualMapping = DefaultParameterResolver.mapFunctionParametersToDummyVariables(program)

        val expectedMappingSimplified = referenceMapOf(
            cParameter to Variable(Variable.Kind.VALUE, "test", Type.Number, cValue),
            dParameter to Variable(Variable.Kind.VALUE, "test", Type.Boolean, dValue),
        )

        compareMappings(expectedMappingSimplified, actualMapping)
    }

    @Test fun `test default parameters mapping in functions defined in various places`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność test() {
            {
                czynność f(a: Liczba = 17) { }
                czynność g(b: Liczba = 18) { }
            }

            jeśli (prawda) {
                czynność h(c: Liczba = 19) { }
            } wpp {
                czynność i(d: Liczba = 20) { }
            }

            dopóki (prawda) {
                czynność j(e: Liczba = 21) { }
            }
        }

        */

        val aValue = Expression.NumberLiteral(17)
        val aParameter = Function.Parameter("a", Type.Number, aValue)
        val bValue = Expression.NumberLiteral(18)
        val bParameter = Function.Parameter("b", Type.Number, bValue)
        val cValue = Expression.NumberLiteral(19)
        val cParameter = Function.Parameter("c", Type.Number, cValue)
        val dValue = Expression.NumberLiteral(20)
        val dParameter = Function.Parameter("d", Type.Number, dValue)
        val eValue = Expression.NumberLiteral(21)
        val eParameter = Function.Parameter("e", Type.Number, eValue)

        val fFunction = Function("f", listOf(aParameter), Type.Unit, listOf())
        val gFunction = Function("g", listOf(bParameter), Type.Unit, listOf())
        val hFunction = Function("h", listOf(cParameter), Type.Unit, listOf())
        val iFunction = Function("i", listOf(dParameter), Type.Unit, listOf())
        val jFunction = Function("j", listOf(eParameter), Type.Unit, listOf())

        val testFunction = Function(
            "test", listOf(), Type.Unit,
            listOf(
                Statement.Block(listOf(Statement.FunctionDefinition(fFunction), Statement.FunctionDefinition(gFunction))),
                Statement.Conditional(Expression.BooleanLiteral(true), listOf(Statement.FunctionDefinition(hFunction)), listOf(Statement.FunctionDefinition(iFunction))),
                Statement.Loop(Expression.BooleanLiteral(true), listOf(Statement.FunctionDefinition(jFunction))),
            )
        )
        val globals = listOf(
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val actualMapping = DefaultParameterResolver.mapFunctionParametersToDummyVariables(program)

        val expectedMappingSimplified = referenceMapOf(
            aParameter to Variable(Variable.Kind.VALUE, "test", Type.Number, aValue),
            bParameter to Variable(Variable.Kind.VALUE, "test", Type.Number, bValue),
            cParameter to Variable(Variable.Kind.VALUE, "test", Type.Number, cValue),
            dParameter to Variable(Variable.Kind.VALUE, "test", Type.Number, dValue),
            eParameter to Variable(Variable.Kind.VALUE, "test", Type.Number, eValue),
        )

        compareMappings(expectedMappingSimplified, actualMapping)
    }

    @Test fun `test reference comparison`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność test() {
            czynność f(a: Liczba = 17) {
                a
            }
            czynność g(a: Liczba = 17) {
                a
            }
        }

        */

        val afValue = Expression.NumberLiteral(17)
        val afParameter = Function.Parameter("a", Type.Number, afValue)
        val agValue = Expression.NumberLiteral(17)
        val agParameter = Function.Parameter("a", Type.Number, agValue)

        val afVariableCall = Expression.Variable("a")
        val agVariableCall = Expression.Variable("a")

        val fFunction = Function("f", listOf(afParameter), Type.Unit, listOf(Statement.Evaluation(afVariableCall)))
        val gFunction = Function("g", listOf(agParameter), Type.Unit, listOf(Statement.Evaluation(agVariableCall)))

        val testFunction = Function(
            "test", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(fFunction),
                Statement.FunctionDefinition(gFunction),
            )
        )
        val globals = listOf(
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val actualMapping = DefaultParameterResolver.mapFunctionParametersToDummyVariables(program)

        val expectedMappingSimplified = referenceMapOf(
            afParameter to Variable(Variable.Kind.VALUE, "test", Type.Number, afValue),
            agParameter to Variable(Variable.Kind.VALUE, "test", Type.Number, agValue),
        )

        compareMappings(expectedMappingSimplified, actualMapping)

        assertNotSame(actualMapping[afParameter]!!, actualMapping[agParameter]!!)
    }
}
