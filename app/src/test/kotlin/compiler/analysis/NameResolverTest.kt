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

        assertContains(nameDefinitions, Ref(fCall1))
        assertContains(nameDefinitions, Ref(fCall2))
        assertContains(nameDefinitions, Ref(xUseInF))
        assertContains(nameDefinitions, Ref(xUseInG))
        assertContains(nameDefinitions, Ref(yUseInG))

        assertEquals(nameDefinitions.size, 5)

        assertEquals(nameDefinitions[Ref(fCall1)], Ref(fFunction))
        assertEquals(nameDefinitions[Ref(fCall2)], Ref(fFunction))
        assertEquals(nameDefinitions[Ref(xUseInF)], Ref(xParam))
        assertEquals(nameDefinitions[Ref(xUseInG)], Ref(xVar))
        assertEquals(nameDefinitions[Ref(yUseInG)], Ref(yParam))

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
