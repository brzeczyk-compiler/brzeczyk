package compiler.analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Type
import compiler.ast.Variable
import compiler.utils.Ref
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

internal class NameResolverTest {
    @Test fun `test proper name resolution`() {
        /*
        Create AST for program:
        ---------------------------------

        zm x: Liczba = 1

        czynność f(x: Liczba) -> Liczba {
            zwróć x + 1
        }

        czynność g(y: Liczba) -> Liczba {
            return f(x) + f(y)
        }
        */

        val xUseInF = Expression.Variable("x")
        val xUseInG = Expression.Variable("x")
        val yUseInG = Expression.Variable("y")

        val fCall1 = Expression.FunctionCall("f", listOf(Expression.FunctionCall.Argument("y", xUseInG)))
        val fCall2 = Expression.FunctionCall("f", listOf(Expression.FunctionCall.Argument("y", yUseInG)))

        val xVar = Variable(Variable.Kind.VARIABLE, "x", Type.Number, Expression.NumberLiteral(1))

        val xParam = Function.Parameter("x", Type.Number, null)
        val yParam = Function.Parameter("y", Type.Number, null)

        val fFunction = Function(
            "f",
            listOf(xParam),
            Type.Number,
            listOf<Statement>(
                Statement.FunctionReturn(
                    Expression.BinaryOperation(
                        Expression.BinaryOperation.Kind.ADD,
                        xUseInF,
                        Expression.NumberLiteral(1)
                    )
                )
            )
        )

        val gFunction = Function(
            "g",
            listOf(yParam),
            Type.Number,
            listOf<Statement>(
                Statement.FunctionReturn(
                    Expression.BinaryOperation(
                        Expression.BinaryOperation.Kind.ADD,
                        fCall1,
                        fCall2
                    )
                )
            )
        )

        val program = Program(
            listOf(
                Program.Global.VariableDefinition(xVar),
                Program.Global.FunctionDefinition(fFunction),
                Program.Global.FunctionDefinition(gFunction)
            )
        )

        // Perform name resolution for such AST

        val result = NameResolver.calculateNameResolution(program, mockk())
        val nameDefinitions = result.nameDefinitions

        // Check the result mapping
        assertEquals(5, nameDefinitions.size)

        assertContains(nameDefinitions, Ref(fCall1))
        assertContains(nameDefinitions, Ref(fCall2))
        assertContains(nameDefinitions, Ref(xUseInF))
        assertContains(nameDefinitions, Ref(xUseInG))
        assertContains(nameDefinitions, Ref(yUseInG))

        assertEquals(nameDefinitions[Ref(fCall1)], Ref(fFunction))
        assertEquals(nameDefinitions[Ref(fCall2)], Ref(fFunction))
        assertEquals(nameDefinitions[Ref(xUseInF)], Ref(xParam))
        assertEquals(nameDefinitions[Ref(xUseInG)], Ref(xVar))
        assertEquals(nameDefinitions[Ref(yUseInG)], Ref(yParam))

        assertEquals(1, result.programStaticDepth)
    }

    @Test fun `test name resolution with generator`() {
        /*
        Create AST for program:
        ---------------------------------
        przekaźnik f(a: Liczba) -> Liczba {
            zm x: Liczba = 1
            dopóki (x <= a) {
                przekaż x
                x = x + 1
                if (x >= 42)
                    zakończ
            }
        }

        czynność główna() {
            otrzymując x: Liczba od f(5) {
                x = 5
            }
        }
        */

        val fUse = Expression.FunctionCall(
            "f",
            listOf(Expression.FunctionCall.Argument(null, Expression.NumberLiteral(5)))
        )
        val xMain = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xMainAssignment = Statement.Assignment("x", Expression.NumberLiteral(5))

        val aF = Function.Parameter("a", Type.Number, null)
        val xF = Variable(Variable.Kind.VARIABLE, "x", Type.Number, Expression.NumberLiteral(1))

        val xFYield = Expression.Variable("x")
        val xFCmp1 = Expression.Variable("x")
        val aFCmp = Expression.Variable("a")
        val fCmp1 = Expression.BinaryOperation(Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS, xFCmp1, aFCmp)
        val xFRead = Expression.Variable("x")
        val xFAssign = Statement.Assignment(
            "x",
            Expression.BinaryOperation(
                Expression.BinaryOperation.Kind.ADD,
                xFRead,
                Expression.NumberLiteral(1)
            )
        )
        val xFCmp2 = Expression.Variable("x")
        val fCmp2 = Expression.BinaryOperation(
            Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS,
            xFCmp2,
            Expression.NumberLiteral(42)
        )

        val fGenerator = Function(
            "f",
            listOf(aF),
            Type.Number,
            listOf(
                Statement.VariableDefinition(xF),
                Statement.Loop(
                    fCmp1,
                    listOf(
                        Statement.GeneratorYield(xFYield),
                        xFAssign,
                        Statement.Conditional(
                            fCmp2,
                            listOf(
                                Statement.FunctionReturn(
                                    Expression.UnitLiteral()
                                )
                            ),
                            null
                        )
                    )
                )
            ),
            true
        )

        val program = Program(
            listOf(
                Program.Global.FunctionDefinition(fGenerator),
                Program.Global.FunctionDefinition(
                    Function(
                        "główna",
                        listOf(),
                        Type.Unit,
                        listOf(Statement.ForeachLoop(xMain, fUse, listOf(xMainAssignment))),
                        false
                    )
                )
            )
        )

        val result = NameResolver.calculateNameResolution(program, mockk())
        val nameDefinitions = result.nameDefinitions

        assertEquals(8, nameDefinitions.size)

        assertContains(nameDefinitions, Ref(xFCmp1))
        assertContains(nameDefinitions, Ref(aFCmp))
        assertContains(nameDefinitions, Ref(xFYield))
        assertContains(nameDefinitions, Ref(xFRead))
        assertContains(nameDefinitions, Ref(xFAssign))
        assertContains(nameDefinitions, Ref(xFCmp2))
        assertContains(nameDefinitions, Ref(fUse))
        assertContains(nameDefinitions, Ref(xMainAssignment))

        assertEquals(nameDefinitions[Ref(xFCmp1)], Ref(xF))
        assertEquals(nameDefinitions[Ref(aFCmp)], Ref(aF))
        assertEquals(nameDefinitions[Ref(xFYield)], Ref(xF))
        assertEquals(nameDefinitions[Ref(xFRead)], Ref(xF))
        assertEquals(nameDefinitions[Ref(xFAssign)], Ref(xF))
        assertEquals(nameDefinitions[Ref(xFCmp2)], Ref(xF))
        assertEquals(nameDefinitions[Ref(fUse)], Ref(fGenerator))
        assertEquals(nameDefinitions[Ref(xMainAssignment)], Ref(xMain))

        assertEquals(1, result.programStaticDepth)
    }

    @Test fun `test non-trivial static depth`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() -> Nic {}

        czynność g() -> Nic {
            czynność h() -> Nic {}
            czynność g() -> Nic {
                czynność f() -> Nic {}
            }
        }

        czynność k() -> Nic {
            czynność k() -> Nic {}
        }
        */

        val program = Program(
            listOf(
                simpleGlobalFunction("f"),
                simpleGlobalFunction(
                    "g",
                    listOf(
                        simpleInnerFunction("h"),
                        simpleInnerFunction(
                            "g",
                            listOf(
                                simpleInnerFunction("f")
                            )
                        )
                    )
                ),
                simpleGlobalFunction(
                    "k",
                    listOf(
                        simpleInnerFunction("k")
                    )
                )
            )
        )

        val result = NameResolver.calculateNameResolution(program, mockk())

        assertEquals(3, result.programStaticDepth)
    }

    private fun simpleGlobalFunction(name: String, body: StatementBlock = listOf()): Program.Global.FunctionDefinition {
        return Program.Global.FunctionDefinition(Function(name, listOf(), Type.Unit, body))
    }

    private fun simpleInnerFunction(name: String, body: StatementBlock = listOf()): Statement.FunctionDefinition {
        return Statement.FunctionDefinition(Function(name, listOf(), Type.Unit, body))
    }
}
