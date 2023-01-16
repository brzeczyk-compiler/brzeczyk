package compiler.analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.utils.Ref
import compiler.utils.keyRefMapOf
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratorAnalyzerTest {

    @Test fun `test generator with no foreach loop inside`() {
        /*
        przekaźnik test() { }
        */

        val testGenerator = Function("test", listOf(), Type.Unit, listOf(), true)
        val globals = listOf(
            Program.Global.FunctionDefinition(testGenerator),
        )

        val program = Program(globals)
        val actualMapping = GeneratorAnalyzer.listForeachLoopsInGenerators(program)

        val expectedMapping = keyRefMapOf(
            testGenerator to listOf<Ref<Statement.ForeachLoop>>(),
        )

        assertEquals(actualMapping, expectedMapping)
    }

    @Test fun `test function is not mapped`() {
        /*
        przekaźnik test() { }
        czynność test() { }
        */

        val testGenerator = Function("test", listOf(), Type.Unit, listOf(), true)
        val testFunction = Function("test", listOf(), Type.Unit, listOf(), false)
        val globals = listOf(
            Program.Global.FunctionDefinition(testGenerator),
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val actualMapping = GeneratorAnalyzer.listForeachLoopsInGenerators(program)

        val expectedMapping = keyRefMapOf(
            testGenerator to listOf<Ref<Statement.ForeachLoop>>(),
        )

        assertEquals(actualMapping, expectedMapping)
    }

    @Test fun `test nested generator with no foreach loop`() {
        /*
        czynność test() {
            przekaźnik test() { }
        }
        */

        val testGenerator = Function("test", listOf(), Type.Unit, listOf(), true)
        val testFunction = Function("test", listOf(), Type.Unit, listOf(Statement.FunctionDefinition(testGenerator)), false)
        val globals = listOf(
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val actualMapping = GeneratorAnalyzer.listForeachLoopsInGenerators(program)

        val expectedMapping = keyRefMapOf(
            testGenerator to listOf<Ref<Statement.ForeachLoop>>(),
        )

        assertEquals(actualMapping, expectedMapping)
    }

    @Test fun `test generator with foreach loop`() {
        /*
        przekaźnik f() -> Liczba { }

        przekaźnik testGlobal() {
            otrzymując x: Liczba od f() {}
        }
        czynność test() {
            przekaźnik testNested() {
                otrzymując x: Liczba od f() {}
            }
        }
        */
        val variableX = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val forEach1 = Statement.ForeachLoop(variableX, Expression.FunctionCall("f", listOf()), listOf())
        val forEach2 = Statement.ForeachLoop(variableX, Expression.FunctionCall("f", listOf()), listOf())

        val generator = Function("f", listOf(), Type.Number, listOf(), true)
        val testGeneratorGlobal = Function("testGlobal", listOf(), Type.Unit, listOf(forEach1), true)
        val testGeneratorNested = Function("testNested", listOf(), Type.Unit, listOf(forEach2), true)
        val testFunction = Function("test", listOf(), Type.Unit, listOf(Statement.FunctionDefinition(testGeneratorNested)), false)
        val globals = listOf(
            Program.Global.FunctionDefinition(generator),
            Program.Global.FunctionDefinition(testGeneratorGlobal),
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val actualMapping = GeneratorAnalyzer.listForeachLoopsInGenerators(program)

        val expectedMapping = keyRefMapOf(
            generator to listOf(),
            testGeneratorGlobal to listOf(Ref(forEach1)),
            testGeneratorNested to listOf(Ref(forEach2)),
        )

        assertEquals(actualMapping, expectedMapping)
    }

    @Test fun `test deeply nested generators`() {
        /*
        przekaźnik f() -> Liczba { }

        czynność test() {
            przekaźnik testNested1() {
                przekaźnik testNested2() {
                    przekaźnik testNested3() {
                        otrzymując x: Liczba od f() {}
                    }
                    otrzymując x: Liczba od f() {}
                }
                otrzymując x: Liczba od f() {}
            }
        }
        */
        val variableX = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val forEach1 = Statement.ForeachLoop(variableX, Expression.FunctionCall("f", listOf()), listOf())
        val forEach2 = Statement.ForeachLoop(variableX, Expression.FunctionCall("f", listOf()), listOf())
        val forEach3 = Statement.ForeachLoop(variableX, Expression.FunctionCall("f", listOf()), listOf())

        val generator = Function("f", listOf(), Type.Number, listOf(), true)
        val testNested3 = Function("testNested3", listOf(), Type.Unit, listOf(forEach3), true)
        val testNested2 = Function("testNested2", listOf(), Type.Unit, listOf(Statement.FunctionDefinition(testNested3), forEach2), true)
        val testNested1 = Function("testNested1", listOf(), Type.Unit, listOf(Statement.FunctionDefinition(testNested2), forEach1), true)
        val testFunction = Function("test", listOf(), Type.Unit, listOf(Statement.FunctionDefinition(testNested1)), false)
        val globals = listOf(
            Program.Global.FunctionDefinition(generator),
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val actualMapping = GeneratorAnalyzer.listForeachLoopsInGenerators(program)

        val expectedMapping = keyRefMapOf(
            generator to listOf(),
            testNested1 to listOf(Ref(forEach3), Ref(forEach2), Ref(forEach1)),
            testNested2 to listOf(Ref(forEach3), Ref(forEach2)),
            testNested3 to listOf(Ref(forEach3)),
        )

        assertEquals(actualMapping, expectedMapping)
    }

    @Test fun `test foreach loops in blocks`() {
        /*
        przekaźnik f() -> Liczba { }

        czynność test() {
            przekaźnik testNested() {
                {
                    otrzymując x: Liczba od f() {}
                }

                jeśli (prawda) {
                    otrzymując x: Liczba od f() {}
                } wpp {
                    otrzymując x: Liczba od f() {}
                }

                dopóki (prawda) {
                    otrzymując x: Liczba od f() {}
                }

                otrzymując x: Liczba od f() {
                    otrzymując x: Liczba od f() {}
                }
            }
        }
        */
        val variableX = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val forEach1 = Statement.ForeachLoop(variableX, Expression.FunctionCall("f", listOf()), listOf())
        val forEach2 = Statement.ForeachLoop(variableX, Expression.FunctionCall("f", listOf()), listOf())
        val forEach3 = Statement.ForeachLoop(variableX, Expression.FunctionCall("f", listOf()), listOf())
        val forEach4 = Statement.ForeachLoop(variableX, Expression.FunctionCall("f", listOf()), listOf())
        val forEach5 = Statement.ForeachLoop(variableX, Expression.FunctionCall("f", listOf()), listOf())
        val forEachBlock = Statement.ForeachLoop(variableX, Expression.FunctionCall("f", listOf()), listOf(forEach5))

        val generator = Function("f", listOf(), Type.Number, listOf(), true)
        val testGenerator = Function(
            "testNested", listOf(), Type.Unit,
            listOf(
                Statement.Block(listOf(forEach1)),
                Statement.Conditional(Expression.BooleanLiteral(true), listOf(forEach2), listOf(forEach3)),
                Statement.Loop(Expression.BooleanLiteral(true), listOf(forEach4)),
                forEachBlock,
            ),
            true,
        )
        val testFunction = Function("test", listOf(), Type.Unit, listOf(Statement.FunctionDefinition(testGenerator)))
        val globals = listOf(
            Program.Global.FunctionDefinition(generator),
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val actualMapping = GeneratorAnalyzer.listForeachLoopsInGenerators(program)

        val expectedMapping = keyRefMapOf(
            generator to listOf(),
            testGenerator to listOf(Ref(forEach1), Ref(forEach2), Ref(forEach3), Ref(forEach4), Ref(forEachBlock), Ref(forEach5)),
        )

        assertEquals(actualMapping, expectedMapping)
    }
}
