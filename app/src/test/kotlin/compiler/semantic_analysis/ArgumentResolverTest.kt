package compiler.semantic_analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Type
import compiler.common.diagnostics.CompilerDiagnostics
import compiler.common.diagnostics.Diagnostic
import compiler.common.reference_collections.referenceMapOf
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ArgumentResolverTest {
    private fun namedArgument(name: String?, expr: Expression) = Expression.FunctionCall.Argument(name, expr)
    private fun argument(expr: Expression) = Expression.FunctionCall.Argument(null, expr)
    private fun intArgument(integerValue: Int) = argument(Expression.NumberLiteral(integerValue))
    private fun boolArgument(booleanValue: Boolean) = argument(Expression.BooleanLiteral(booleanValue))
    private fun namedIntArgument(name: String, value: Int) =
        namedArgument(name, Expression.NumberLiteral(value))
    private fun namedBoolArgument(name: String, value: Boolean) =
        namedArgument(name, Expression.BooleanLiteral(value))
    private fun functionCall(name: String, arguments: List<Expression.FunctionCall.Argument>) =
        Expression.FunctionCall(name, arguments)
    private fun globalFunction(name: String, parameters: List<Function.Parameter>, body: StatementBlock = emptyList()) =
        Program.Global.FunctionDefinition(Function(name, parameters, Type.Unit, body))
    private fun mainFunction(body: StatementBlock) = globalFunction("główna", emptyList(), body)
    private fun localFunction(name: String, parameters: List<Function.Parameter>, body: StatementBlock = emptyList()) =
        Statement.FunctionDefinition(Function(name, parameters, Type.Unit, body))
    private fun intParameter(name: String) = Function.Parameter(name, Type.Number, null)
    private fun boolParameter(name: String) = Function.Parameter(name, Type.Boolean, null)
    private fun defaultIntParameter(name: String, value: Int) =
        Function.Parameter(name, Type.Boolean, Expression.NumberLiteral(value))
    private fun defaultBoolParameter(name: String, value: Boolean) =
        Function.Parameter(name, Type.Boolean, Expression.BooleanLiteral(value))

    @Test
    fun `test simple argument resolution`() {
        /*
        czynność f(a: Liczba) {}
        czynność główna() {
            f(4)
        }
        */
        val parameter = intParameter("a")
        val function = globalFunction("f", listOf(parameter), emptyList())
        val argument = intArgument(4)
        val call = functionCall("f", listOf(argument))
        val main = mainFunction(listOf(Statement.Evaluation(call)))
        val program = Program(listOf(function, main))
        val nameResolution = referenceMapOf<Any, NamedNode>(call to function.function)
        val diagnostics = CompilerDiagnostics()

        val argumentResolution = ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)

        assertEquals(emptyList(), diagnostics.diagnostics.toList())
        assertEquals(referenceMapOf(argument to parameter), argumentResolution)
    }

    @Test
    fun `test named argument resolution`() {
        /*
        czynność f(a: Liczba, b: Liczba, c: Liczba) {}
        czynność główna() {
            f(1, c=3, b=2)
        }
        */
        val parA = intParameter("a")
        val parB = intParameter("b")
        val parC = intParameter("c")
        val function = globalFunction("f", listOf(parA, parB, parC), emptyList())
        val arg1 = intArgument(4)
        val arg2 = namedIntArgument("c", 3)
        val arg3 = namedIntArgument("b", 3)
        val call = functionCall("f", listOf(arg1, arg2, arg3))
        val main = mainFunction(listOf(Statement.Evaluation(call)))
        val program = Program(listOf(function, main))
        val nameResolution = referenceMapOf<Any, NamedNode>(call to function.function)
        val diagnostics = CompilerDiagnostics()

        val argumentResolution = ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)

        val expected = referenceMapOf(arg1 to parA, arg2 to parC, arg3 to parB)

        assertEquals(emptyList(), diagnostics.diagnostics.toList())
        assertEquals(expected, argumentResolution)
    }

    @Test
    fun `test argument resolution with default parameters`() {
        /*
        czynność f(a: Liczba, b: Liczba = 10, c: Liczba = 13) {}
        czynność główna() {
            f(1, c=3)
        }
        */
        val parA = intParameter("a")
        val parB = defaultIntParameter("b", 10)
        val parC = defaultIntParameter("c", 13)
        val function = globalFunction("f", listOf(parA, parB, parC), emptyList())
        val arg1 = intArgument(4)
        val arg2 = namedIntArgument("c", 3)
        val call = functionCall("f", listOf(arg1, arg2))
        val main = mainFunction(listOf(Statement.Evaluation(call)))
        val program = Program(listOf(function, main))
        val nameResolution = referenceMapOf<Any, NamedNode>(call to function.function)
        val diagnostics = CompilerDiagnostics()

        val argumentResolution = ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)

        val expected = referenceMapOf(arg1 to parA, arg2 to parC)

        assertEquals(emptyList(), diagnostics.diagnostics.toList())
        assertEquals(expected, argumentResolution)
    }

    @Test
    fun `test argument resolution with all arguments being default`() {
        /*
        czynność f(a: Liczba = 0, b: Liczba = 10, c: Liczba = 13) {}
        czynność główna() {
            f()
        }
        */
        val parA = defaultIntParameter("a", 0)
        val parB = defaultIntParameter("b", 10)
        val parC = defaultIntParameter("c", 13)
        val function = globalFunction("f", listOf(parA, parB, parC), emptyList())
        val call = functionCall("f", listOf())
        val main = mainFunction(listOf(Statement.Evaluation(call)))
        val program = Program(listOf(function, main))
        val nameResolution = referenceMapOf<Any, NamedNode>(call to function.function)
        val diagnostics = CompilerDiagnostics()

        val argumentResolution = ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)

        assertEquals(emptyList(), diagnostics.diagnostics.toList())
        assertEquals(referenceMapOf(), argumentResolution)
    }

    @Test
    fun `test argument resolution for local function`() {
        /*
        czynność f() {
            czynność g(a: Czy) { }
            g(1111)
        }
        */
        val arg = intArgument(1111)
        val call = functionCall("g", listOf(arg))
        val par = intParameter("a")
        val inner = localFunction("g", listOf(par))
        val outer = globalFunction("f", emptyList(), listOf(inner, Statement.Evaluation(call)))
        val program = Program(listOf(outer))
        val nameResolution = referenceMapOf<Any, NamedNode>(call to inner.function)
        val diagnostics = CompilerDiagnostics()

        val argumentResolution = ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)

        val expected = referenceMapOf(arg to par)

        assertEquals(emptyList(), diagnostics.diagnostics.toList())
        assertEquals(expected, argumentResolution)
    }

    @Test
    fun `test wrong default parameter order`() {
        /*
        czynność f(a: Czy = prawda, b: Czy) {}
         */
        val par1 = defaultBoolParameter("a", true)
        val par2 = boolParameter("b")
        val function = globalFunction("f", listOf(par1, par2))
        val program = Program(listOf(function))
        val nameResolution = referenceMapOf<Any, NamedNode>()
        val diagnostics = CompilerDiagnostics()

        ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)

        val expected = listOf(Diagnostic.ArgumentResolutionError.DefaultParametersNotLast(function.function))

        assertEquals(expected, diagnostics.diagnostics.toList())
    }

    @Test
    fun `test positional argument after named`() {
        /*
        czynność f(a: Liczba, b: Liczba) {}
        czynnosć główna() {
            f(a=10, 5)
        }
         */
        val par1 = intParameter("a")
        val par2 = intParameter("b")
        val function = globalFunction("f", listOf(par1, par2))
        val arg1 = namedIntArgument("a", 10)
        val arg2 = intArgument(5)
        val call = functionCall("f", listOf(arg1, arg2))
        val main = mainFunction(listOf(Statement.Evaluation(call)))
        val program = Program(listOf(function, main))
        val nameResolution = referenceMapOf<Any, NamedNode>(call to function.function)
        val diagnostics = CompilerDiagnostics()

        ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)

        val expected = listOf(Diagnostic.ArgumentResolutionError.PositionalArgumentAfterNamed(call))

        assertEquals(expected, diagnostics.diagnostics.toList())
    }

    @Test
    fun `test missing argument`() {
        /*
        czynność f(a: Czy, b: Czy) {}
        czynnosć główna() {
            f(fałsz)
        }
         */
        val par1 = boolParameter("a")
        val par2 = boolParameter("b")
        val function = globalFunction("f", listOf(par1, par2))
        val arg1 = boolArgument(false)
        val call = functionCall("f", listOf(arg1))
        val main = mainFunction(listOf(Statement.Evaluation(call)))
        val program = Program(listOf(function, main))
        val nameResolution = referenceMapOf<Any, NamedNode>(call to function.function)
        val diagnostics = CompilerDiagnostics()

        ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)

        val expected = listOf(Diagnostic.ArgumentResolutionError.MissingArgument(call, "b"))

        assertEquals(expected, diagnostics.diagnostics.toList())
    }

    @Test
    fun `test too many arguments in a function call`() {
        /*
        czynność f(a: Czy, b: Czy) {}
        czynnosć główna() {
            f(fałsz, prawda, 10)
        }
         */
        val par1 = boolParameter("a")
        val par2 = boolParameter("b")
        val function = globalFunction("f", listOf(par1, par2))
        val arg1 = boolArgument(false)
        val arg2 = boolArgument(true)
        val arg3 = intArgument(10)
        val call = functionCall("f", listOf(arg1, arg2, arg3))
        val main = mainFunction(listOf(Statement.Evaluation(call)))
        val program = Program(listOf(function, main))
        val nameResolution = referenceMapOf<Any, NamedNode>(call to function.function)
        val diagnostics = CompilerDiagnostics()

        ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)

        val expected = listOf(Diagnostic.ArgumentResolutionError.TooManyArguments(call))

        assertEquals(expected, diagnostics.diagnostics.toList())
    }

    @Test
    fun `test repeated argument in function call`() {
        /*
        czynność f(a: Czy, b: Czy) {}
        czynnosć główna() {
            f(fałsz, fałsz, a=prawda)
        }
         */
        val par1 = boolParameter("a")
        val par2 = boolParameter("b")
        val function = globalFunction("f", listOf(par1, par2))
        val arg1 = boolArgument(false)
        val arg2 = boolArgument(true)
        val arg3 = namedBoolArgument("a", true)
        val call = functionCall("f", listOf(arg1, arg2, arg3))
        val main = mainFunction(listOf(Statement.Evaluation(call)))
        val program = Program(listOf(function, main))
        val nameResolution = referenceMapOf<Any, NamedNode>(call to function.function)
        val diagnostics = CompilerDiagnostics()

        ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)

        val expected = listOf(Diagnostic.ArgumentResolutionError.RepeatedArgument(call, "a"))

        assertEquals(expected, diagnostics.diagnostics.toList())
    }

    @Test
    fun `test unknown named argument in function call`() {
        /*
        czynność f(a: Czy, b: Czy) {}
        czynnosć główna() {
            f(fałsz, fałsz, c=prawda)
        }
         */
        val par1 = boolParameter("a")
        val par2 = boolParameter("b")
        val function = globalFunction("f", listOf(par1, par2))
        val arg1 = boolArgument(false)
        val arg2 = boolArgument(true)
        val arg3 = namedBoolArgument("c", true)
        val call = functionCall("f", listOf(arg1, arg2, arg3))
        val main = mainFunction(listOf(Statement.Evaluation(call)))
        val program = Program(listOf(function, main))
        val nameResolution = referenceMapOf<Any, NamedNode>(call to function.function)
        val diagnostics = CompilerDiagnostics()

        ArgumentResolver.calculateArgumentToParameterResolution(program, nameResolution, diagnostics)

        val expected = listOf(Diagnostic.ArgumentResolutionError.UnknownArgument(call, "c"))

        assertEquals(expected, diagnostics.diagnostics.toList())
    }
}
