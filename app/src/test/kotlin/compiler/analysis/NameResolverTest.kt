package compiler.analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
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

        val nameDefinitions = NameResolver.calculateNameResolution(program, mockk())

        // Check the result mapping

        assertContains(nameDefinitions, fCall1)
        assertContains(nameDefinitions, fCall2)
        assertContains(nameDefinitions, xUseInF)
        assertContains(nameDefinitions, xUseInG)
        assertContains(nameDefinitions, yUseInG)

        assertEquals(nameDefinitions.size, 5)

        assertEquals(nameDefinitions[fCall1], fFunction)
        assertEquals(nameDefinitions[fCall2], fFunction)
        assertEquals(nameDefinitions[xUseInF], xParam)
        assertEquals(nameDefinitions[xUseInG], xVar)
        assertEquals(nameDefinitions[yUseInG], yParam)
    }
}
