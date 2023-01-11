package compiler.analysis

import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Program.Global
import compiler.ast.Program.Global.FunctionDefinition
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.diagnostics.CompilerDiagnostics
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.VariableInitializationError
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.VariableInitializationError.ReferenceToUninitializedVariable
import compiler.utils.Ref
import compiler.utils.keyRefMapOf
import compiler.utils.refMapOf
import kotlin.test.Test
import kotlin.test.assertEquals

class InitializationVerifierTest {
    private fun checkDiagnostics(
        program: Program,
        nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>,
        expectedDiagnostics: List<VariableInitializationError>
    ) {
        val actualDiagnostics = CompilerDiagnostics()
        InitializationVerifier.verifyAccessedVariablesAreInitialized(program, nameResolution, keyRefMapOf(), actualDiagnostics)
        assertEquals(expectedDiagnostics, actualDiagnostics.diagnostics.filterIsInstance<VariableInitializationError>().toList())
    }

    // czynność zewnętrzna() {
    //     zm y: Liczba = x
    // }
    // zm x: Liczba = 5
    @Test
    fun `test reference to global variable below function is correct`() {
        val globalX = Variable(Variable.Kind.VALUE, "x", Type.Number, Expression.NumberLiteral(5))
        val globalXDefinition = Global.VariableDefinition(globalX)
        val readFromX = Expression.Variable("x")

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function), globalXDefinition))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            readFromX to globalX,
            globalXDefinition to globalX
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     zm y: Liczba = x
    // }
    @Test
    fun `test reference to unitialized variable defined right above is incorrect`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val readFromX = Expression.Variable("x")

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     x = 123
    //     zm y: Liczba = x
    // }
    @Test
    fun `test reference after late initialization is correct`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, assignmentToX, yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     { x = 123 }
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in statement block is correct`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val blockWithAssignment = Statement.Block(listOf(assignmentToX))
        val readFromX = Expression.Variable("x")

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, blockWithAssignment, yDefinition)
        )
        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     jeśli (fałsz) {
    //          x = 123
    //     }
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in one side of conditional is incorrect`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val conditionalAssignmentToX = Statement.Conditional(Expression.BooleanLiteral(false), listOf(assignmentToX), null)
        val readFromX = Expression.Variable("x")

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, conditionalAssignmentToX, yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     jeśli (fałsz) zwróć
    //     zm y: Liczba = x
    // }
    @Test
    fun `test reference to uninitialized variable after a potential return is incorrect`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val readFromX = Expression.Variable("x")

        val returningIf = Statement.Conditional(
            Expression.BooleanLiteral(false),
            listOf(Statement.FunctionReturn(Expression.UnitLiteral())), null
        )

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, returningIf, yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     jeśli (fałsz) {
    //          x = 123
    //     } wpp {
    //          x = 124
    //     }
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in both sides of a conditional is correct`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val alternativeAssignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(124))
        val trickyAssignmentToX = Statement.Conditional(
            Expression.BooleanLiteral(false), listOf(assignmentToX),
            listOf(alternativeAssignmentToX)
        )
        val readFromX = Expression.Variable("x")

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, trickyAssignmentToX, yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            alternativeAssignmentToX to x,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() -> Czy {
    //          x = 123
    //          zwróć fałsz
    //     }
    //     jeśli (wewnętrzna()) {
    //          zwróć x
    //     }
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in condition is correct`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Boolean,
            listOf(assignmentToX, Statement.FunctionReturn(Expression.BooleanLiteral(false)))
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())

        val trickyAssignmentToX = Statement.Conditional(innerFunctionCall, listOf(Statement.FunctionReturn(readFromX)), null)

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, trickyAssignmentToX, yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            innerFunctionCall to innerFunction,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     dopóki (fałsz) {
    //          x = 123
    //     }
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in loop body is incorrect`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val loopWithAssignment = Statement.Loop(Expression.BooleanLiteral(false), listOf(assignmentToX))
        val readFromX = Expression.Variable("x")

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, loopWithAssignment, yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     przekaźnik wewnętrzny() -> Liczba {
    //          zakończ
    //     }
    //     dostając z: Liczba od wewnętrzny() {
    //          x = 123
    //     }
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in foreach body is incorrect`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val innerGenerator = Function(
            "wewnętrzny", listOf(), Type.Number,
            listOf(Statement.FunctionReturn(Expression.UnitLiteral()))
        )
        val innerGeneratorCall = Expression.FunctionCall("wewnętrzny", listOf())
        val z = Variable(Variable.Kind.VALUE, "z", Type.Number, null)
        val assignmentInForEach = Statement.ForeachLoop(z, innerGeneratorCall, listOf(assignmentToX))

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, Statement.FunctionDefinition(innerGenerator), assignmentInForEach, yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            innerGeneratorCall to innerGenerator,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     przekaźnik wewnętrzny() -> Liczba {
    //          przekaż 124
    //          x = 123
    //          zakończ
    //     }
    //     dostając z: Liczba od wewnętrzny() { }
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization after yield is incorrect`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val innerGenerator = Function(
            "wewnętrzny", listOf(), Type.Number,
            listOf(
                Statement.GeneratorYield(
                    Expression.NumberLiteral(124)
                ),
                assignmentToX,
                Statement.FunctionReturn(Expression.UnitLiteral())
            )
        )
        val innerGeneratorCall = Expression.FunctionCall("wewnętrzny", listOf())
        val z = Variable(Variable.Kind.VALUE, "z", Type.Number, null)
        val basicForEach = Statement.ForeachLoop(z, innerGeneratorCall, listOf())

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, Statement.FunctionDefinition(innerGenerator), basicForEach, yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            innerGeneratorCall to innerGenerator,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() -> Liczba {
    //          x = 123
    //          zwróć 124
    //     }
    //     wewnętrzna()
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in inner function call is correct`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Number,
            listOf(assignmentToX, Statement.FunctionReturn(Expression.NumberLiteral(124)))
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(
                xDefinition, Statement.FunctionDefinition(innerFunction),
                Statement.Evaluation(innerFunctionCall), yDefinition
            )
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x,
            innerFunctionCall to innerFunction
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() -> Liczba {
    //          x = 123
    //          zwróć 124
    //     }
    //     zm y: Liczba = x
    // }
    @Test
    fun `test assignment in function definition is not enough`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Number,
            listOf(assignmentToX, Statement.FunctionReturn(Expression.NumberLiteral(124)))
        )

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, Statement.FunctionDefinition(innerFunction), yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x,
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() -> Liczba {
    //          x = 123
    //          zwróć 124
    //     }
    //     zm z: Liczba = prawda ? wewnętrzna() : 120
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in only one side of conditional assignment is incorrect`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Number,
            listOf(assignmentToX, Statement.FunctionReturn(Expression.NumberLiteral(124)))
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())

        val z = Variable(
            Variable.Kind.VALUE, "z", Type.Number,
            Expression.Conditional(Expression.BooleanLiteral(true), innerFunctionCall, Expression.NumberLiteral(120))
        )

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, Statement.FunctionDefinition(innerFunction), Statement.VariableDefinition(z), yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x,
            innerFunctionCall to innerFunction,
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() -> Liczba {
    //          x = 123
    //          zwróć 124
    //     }
    //     zm z: Liczba = prawda ? wewnętrzna() : wewnętrzna()
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in both sides of conditional assignment is correct`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Number, listOf(assignmentToX, Statement.FunctionReturn(Expression.NumberLiteral(124)))
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())

        val z = Variable(
            Variable.Kind.VALUE, "z", Type.Number,
            Expression.Conditional(Expression.BooleanLiteral(true), innerFunctionCall, innerFunctionCall)
        )

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, Statement.FunctionDefinition(innerFunction), Statement.VariableDefinition(z), yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x,
            innerFunctionCall to innerFunction,
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() -> Czy  {
    //          x = 123
    //          zwróć fałsz
    //     }
    //     zm z: Czy = fałsz oraz wewnętrzna()
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in right side of short circuit is incorrect`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Boolean,
            listOf(assignmentToX, Statement.FunctionReturn(Expression.BooleanLiteral(false)))
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())

        val z = Variable(
            Variable.Kind.VALUE, "z", Type.Boolean,
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.OR, Expression.BooleanLiteral(false), innerFunctionCall)
        )

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, Statement.FunctionDefinition(innerFunction), Statement.VariableDefinition(z), yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x,
            innerFunctionCall to innerFunction,
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() -> Czy  {
    //          x = 123
    //          zwróć fałsz
    //     }
    //     zm z: Czy = wewnętrzna() oraz fałsz
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in left side of short circuit is correct`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Boolean,
            listOf(assignmentToX, Statement.FunctionReturn(Expression.BooleanLiteral(false)))
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())

        val z = Variable(
            Variable.Kind.VALUE, "z", Type.Boolean,
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.OR, innerFunctionCall, Expression.BooleanLiteral(false))
        )

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, Statement.FunctionDefinition(innerFunction), Statement.VariableDefinition(z), yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x,
            innerFunctionCall to innerFunction,
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() -> Liczba {
    //          x = 123
    //          zwróć 124
    //     }
    //     czynność wewnętrzna2() -> Liczba {
    //          zwróć wewnętrzna()
    //     }
    //     wewnętrzna2()
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in function return is correct`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Number,
            listOf(assignmentToX, Statement.FunctionReturn(Expression.NumberLiteral(124)))
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())
        val innerFunction2 = Function(
            "wewnętrzna2", listOf(), Type.Number,
            listOf(Statement.FunctionReturn(innerFunctionCall))
        )
        val innerFunction2Call = Expression.FunctionCall("wewnętrzna2", listOf())

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(
                xDefinition, Statement.FunctionDefinition(innerFunction), Statement.FunctionDefinition(innerFunction2),
                Statement.Evaluation(innerFunction2Call),
                yDefinition
            )
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x,
            innerFunctionCall to innerFunction,
            innerFunction2Call to innerFunction2
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() -> Liczba {
    //          x = 123
    //          zwróć 124
    //     }
    //     czynność wewnętrzna2(z: Liczba = wewnętrzna()) -> Liczba {
    //          zwróć 124
    //     }
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization in default parameter is correct`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Number,
            listOf(assignmentToX, Statement.FunctionReturn(Expression.NumberLiteral(124)))
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())
        val innerFunction2 = Function(
            "wewnętrzna2", listOf(Function.Parameter("z", Type.Number, innerFunctionCall)), Type.Number,
            listOf(Statement.FunctionReturn(innerFunctionCall))
        )

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(
                xDefinition, Statement.FunctionDefinition(innerFunction),
                Statement.FunctionDefinition(innerFunction2),
                yDefinition
            )
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x,
            innerFunctionCall to innerFunction,
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() {
    //          jeśli (fałsz) zwróć
    //          x = 123
    //     }
    //     wewnętrzna()
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization after a potential return in nested function call is incorrect`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val returningIf = Statement.Conditional(
            Expression.BooleanLiteral(false),
            listOf(Statement.FunctionReturn(Expression.UnitLiteral())), null
        )
        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Unit,
            listOf(returningIf, assignmentToX)
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, Statement.FunctionDefinition(innerFunction), Statement.Evaluation(innerFunctionCall), yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            innerFunctionCall to innerFunction,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() {
    //          dopóki (fałsz) zwróć
    //          x = 123
    //     }
    //     wewnętrzna()
    //     zm y: Liczba = x
    // }
    @Test
    fun `test initialization after a potential return in a loop in a nested function call is incorrect`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val returningLoop = Statement.Loop(
            Expression.BooleanLiteral(false),
            listOf(Statement.FunctionReturn(Expression.UnitLiteral()))
        )
        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Unit,
            listOf(returningLoop, assignmentToX)
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, Statement.FunctionDefinition(innerFunction), Statement.Evaluation(innerFunctionCall), yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            innerFunctionCall to innerFunction,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() {
    //          x = 123
    //          jeśli (fałsz) zwróć
    //     }
    //     wewnętrzna()
    //     zm y: Liczba = x
    // }
    @Test
    fun `test potential return after initialization is correct`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val returningIf = Statement.Conditional(
            Expression.BooleanLiteral(false),
            listOf(Statement.FunctionReturn(Expression.UnitLiteral())), null
        )
        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Unit,
            listOf(assignmentToX, returningIf)
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, Statement.FunctionDefinition(innerFunction), Statement.Evaluation(innerFunctionCall), yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            innerFunctionCall to innerFunction,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     jeśli (fałsz) {
    //         dopóki (fałsz) zwróć
    //         x = 123
    //     } wpp {
    //         x = 123
    //     }
    //     zm y: Liczba = x
    // }
    @Test
    fun `test return in if at same level does not matter`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val returningLoop = Statement.Loop(
            Expression.BooleanLiteral(false),
            listOf(Statement.FunctionReturn(Expression.UnitLiteral()))
        )
        val trickyIf = Statement.Conditional(
            Expression.BooleanLiteral(false),
            listOf(returningLoop, assignmentToX), listOf(assignmentToX)
        )

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(xDefinition, trickyIf, yDefinition)
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf())
    }

    // czynność zewnętrzna() {
    //     zm x: Liczba
    //     czynność wewnętrzna() {
    //          jeśli (fałsz) {
    //              dopóki (fałsz) zwróć
    //              x = 123
    //          } wpp {
    //              x = 123
    //          }
    //     }
    //     wewnętrzna()
    //     zm y: Liczba = x
    // }
    @Test
    fun `test return in if in nested function does matter`() {
        val x = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val xDefinition = Statement.VariableDefinition(x)
        val assignmentToX = Statement.Assignment(Statement.Assignment.LValue.Variable("x"), Expression.NumberLiteral(123))
        val readFromX = Expression.Variable("x")

        val returningLoop = Statement.Loop(
            Expression.BooleanLiteral(false),
            listOf(Statement.FunctionReturn(Expression.UnitLiteral()))
        )
        val trickyIf = Statement.Conditional(
            Expression.BooleanLiteral(false),
            listOf(returningLoop, assignmentToX), listOf(assignmentToX)
        )
        val innerFunction = Function(
            "wewnętrzna", listOf(), Type.Unit,
            listOf(trickyIf)
        )
        val innerFunctionCall = Expression.FunctionCall("wewnętrzna", listOf())

        val y = Variable(Variable.Kind.VALUE, "y", Type.Number, readFromX)
        val yDefinition = Statement.VariableDefinition(y)
        val function = Function(
            "zewnętrzna", listOf(), Type.Unit,
            listOf(
                xDefinition, Statement.FunctionDefinition(innerFunction),
                Statement.Evaluation(innerFunctionCall), yDefinition
            )
        )

        val program = Program(listOf(FunctionDefinition(function)))
        val nameResolution: Map<Ref<AstNode>, Ref<NamedNode>> = refMapOf(
            assignmentToX to x,
            innerFunctionCall to innerFunction,
            readFromX to x
        )

        checkDiagnostics(program, nameResolution, listOf(ReferenceToUninitializedVariable(x)))
    }
}
