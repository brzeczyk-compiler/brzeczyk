package compiler.analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.utils.Ref
import compiler.utils.assertContentEquals
import compiler.utils.keyRefMapOf
import kotlin.test.Test
import kotlin.test.assertNotSame

class DefaultParameterResolverTest {
    private data class UnnamedVariable(val kind: Variable.Kind, val type: Type, val value: Expression?) {
        constructor(variable: Variable) : this(variable.kind, variable.type, variable.value)
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
        val actualMappingSimplified = DefaultParameterResolver.mapFunctionParametersToDummyVariables(program).mapValues { UnnamedVariable(it.value) }

        val expectedMappingSimplified = keyRefMapOf(
            cParameter to UnnamedVariable(Variable.Kind.CONSTANT, Type.Number, cValue),
            dParameter to UnnamedVariable(Variable.Kind.CONSTANT, Type.Boolean, dValue),
        )

        assertContentEquals(expectedMappingSimplified, actualMappingSimplified)
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
        val actualMappingSimplified = DefaultParameterResolver.mapFunctionParametersToDummyVariables(program).mapValues { UnnamedVariable(it.value) }

        val expectedMappingSimplified = keyRefMapOf(
            cParameter to UnnamedVariable(Variable.Kind.VALUE, Type.Number, cValue),
            dParameter to UnnamedVariable(Variable.Kind.VALUE, Type.Boolean, dValue),
        )

        assertContentEquals(expectedMappingSimplified, actualMappingSimplified)
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
        val actualMappingSimplified = DefaultParameterResolver.mapFunctionParametersToDummyVariables(program).mapValues { UnnamedVariable(it.value) }

        val expectedMappingSimplified = keyRefMapOf(
            aParameter to UnnamedVariable(Variable.Kind.VALUE, Type.Number, aValue),
            bParameter to UnnamedVariable(Variable.Kind.VALUE, Type.Number, bValue),
            cParameter to UnnamedVariable(Variable.Kind.VALUE, Type.Number, cValue),
            dParameter to UnnamedVariable(Variable.Kind.VALUE, Type.Number, dValue),
            eParameter to UnnamedVariable(Variable.Kind.VALUE, Type.Number, eValue),
        )

        assertContentEquals(expectedMappingSimplified, actualMappingSimplified)
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
        val actualMappingSimplified = actualMapping.mapValues { UnnamedVariable(it.value) }

        val expectedMappingSimplified = keyRefMapOf(
            afParameter to UnnamedVariable(Variable.Kind.VALUE, Type.Number, afValue),
            agParameter to UnnamedVariable(Variable.Kind.VALUE, Type.Number, agValue),
        )

        assertContentEquals(expectedMappingSimplified, actualMappingSimplified)

        assertNotSame(actualMapping[Ref(afParameter)]!!, actualMapping[Ref(agParameter)]!!)
    }
}
