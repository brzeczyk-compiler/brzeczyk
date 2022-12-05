package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostic.ControlFlowDiagnostic
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.referenceHashMapOf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FunctionControlFlowTest {
    private val expressionNodes = referenceHashMapOf<Expression, ReferenceHashMap<Variable?, IFTNode>>()
    private val nameResolution = referenceHashMapOf<Any, NamedNode>()
    private val defaultParameterValues = referenceHashMapOf<Function.Parameter, Variable>()
    private val diagnostics = mutableListOf<Diagnostic>()

    private fun addExpressionNode(expression: Expression, variable: Variable?): IFTNode {
        val node = IntermediateFormTreeNode.NoOp()
        expressionNodes.putIfAbsent(expression, referenceHashMapOf())
        expressionNodes[expression]!![variable] = node
        return node
    }

    private fun getExpressionCFG(expression: Expression, variable: Variable?, function: Function): ControlFlowGraph {
        val node = expressionNodes[expression]?.get(variable)
        val nodeList = node?.let { listOf(it) } ?: emptyList()
        return ControlFlowGraph(nodeList, node, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf())
    }

    private fun test(program: Program) = ControlFlow.createGraphForEachFunction(
        program,
        this::getExpressionCFG,
        nameResolution,
        defaultParameterValues,
        diagnostics::add
    ).first

    // czynność f() { }

    @Test
    fun `empty function`() {
        val function = Function("f", listOf(), Type.Unit, listOf())
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val result = test(program)

        val cfg = ControlFlowGraph(listOf(), null, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf())
        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() { 123 }

    @Test
    fun `expression evaluation`() {
        val value = Expression.NumberLiteral(123)
        val evaluation = Statement.Evaluation(value)
        val function = Function("f", listOf(), Type.Unit, listOf(evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node = addExpressionNode(value, null)

        val result = test(program)

        val cfg = ControlFlowGraph(listOf(node), node, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf())
        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() {
    //     123
    //     456
    // }

    @Test
    fun `two expressions evaluation`() {
        val value1 = Expression.NumberLiteral(123)
        val value2 = Expression.NumberLiteral(456)
        val evaluation1 = Statement.Evaluation(value1)
        val evaluation2 = Statement.Evaluation(value2)
        val function = Function("f", listOf(), Type.Unit, listOf(evaluation1, evaluation2))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node1 = addExpressionNode(value1, null)
        val node2 = addExpressionNode(value2, null)

        val result = test(program)

        val cfg = ControlFlowGraph(
            listOf(node1, node2),
            node1,
            referenceHashMapOf(node1 to node2),
            referenceHashMapOf(),
            referenceHashMapOf()
        )

        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() { wart x: Liczba = 123 }

    @Test
    fun `variable definition`() {
        val value = Expression.NumberLiteral(123)
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, value)
        val definition = Statement.VariableDefinition(variable)
        val function = Function("f", listOf(), Type.Unit, listOf(definition))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node = addExpressionNode(value, variable)

        val result = test(program)

        val cfg = ControlFlowGraph(listOf(node), node, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf())
        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() {
    //     czynność g(x: Liczba = 123) { }
    // }

    @Test
    fun `nested function with default parameter value`() {
        val value = Expression.NumberLiteral(123)
        val parameter = Function.Parameter("x", Type.Number, value)
        val parameterVariable = Variable(Variable.Kind.VALUE, "", Type.Number, null)
        defaultParameterValues[parameter] = parameterVariable
        val nestedFunction = Function("g", listOf(parameter), Type.Unit, listOf())
        val definition = Statement.FunctionDefinition(nestedFunction)
        val function = Function("f", listOf(), Type.Unit, listOf(definition))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node = addExpressionNode(value, parameterVariable)

        val result = test(program)

        val cfg = ControlFlowGraph(listOf(node), node, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf())
        val nestedCfg = ControlFlowGraph(listOf(), null, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf())
        assertEquals(referenceHashMapOf(function to cfg, nestedFunction to nestedCfg), result)
    }

    // czynność f() {
    //     wart x: Liczba
    //     x = 123
    // }

    @Test
    fun `variable assignment`() {
        val variable = Variable(Variable.Kind.VALUE, "x", Type.Number, null)
        val definition = Statement.VariableDefinition(variable)
        val value = Expression.NumberLiteral(123)
        val assignment = Statement.Assignment("x", value)
        nameResolution[assignment] = variable
        val function = Function("f", listOf(), Type.Unit, listOf(definition, assignment))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node = addExpressionNode(value, variable)

        val result = test(program)

        val cfg = ControlFlowGraph(listOf(node), node, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf())
        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() { { 123 } }

    @Test
    fun `nested block`() {
        val value = Expression.NumberLiteral(123)
        val evaluation = Statement.Evaluation(value)
        val block = Statement.Block(listOf(evaluation))
        val function = Function("f", listOf(), Type.Unit, listOf(block))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node = addExpressionNode(value, null)

        val result = test(program)

        val cfg = ControlFlowGraph(listOf(node), node, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf())
        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() {
    //     jeśli (prawda) 123
    //     456
    // }

    @Test
    fun `single-branch conditional`() {
        val condition = Expression.BooleanLiteral(true)
        val value1 = Expression.NumberLiteral(123)
        val value2 = Expression.NumberLiteral(456)
        val evaluation1 = Statement.Evaluation(value1)
        val evaluation2 = Statement.Evaluation(value2)
        val conditional = Statement.Conditional(condition, listOf(evaluation1), null)
        val function = Function("f", listOf(), Type.Unit, listOf(conditional, evaluation2))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node1 = addExpressionNode(condition, null)
        val node2 = addExpressionNode(value1, null)
        val node3 = addExpressionNode(value2, null)

        val result = test(program)

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3),
            node1,
            referenceHashMapOf(node2 to node3),
            referenceHashMapOf(node1 to node2),
            referenceHashMapOf(node1 to node3)
        )

        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() {
    //     jeśli (prawda) 123
    //     wpw 456
    //     789
    // }

    @Test
    fun `two-branch conditional`() {
        val condition = Expression.BooleanLiteral(true)
        val value1 = Expression.NumberLiteral(123)
        val value2 = Expression.NumberLiteral(456)
        val value3 = Expression.NumberLiteral(789)
        val evaluation1 = Statement.Evaluation(value1)
        val evaluation2 = Statement.Evaluation(value2)
        val evaluation3 = Statement.Evaluation(value3)
        val conditional = Statement.Conditional(condition, listOf(evaluation1), listOf(evaluation2))
        val function = Function("f", listOf(), Type.Unit, listOf(conditional, evaluation3))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node1 = addExpressionNode(condition, null)
        val node2 = addExpressionNode(value1, null)
        val node3 = addExpressionNode(value2, null)
        val node4 = addExpressionNode(value3, null)

        val result = test(program)

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3, node4),
            node1,
            referenceHashMapOf(node2 to node4, node3 to node4),
            referenceHashMapOf(node1 to node2),
            referenceHashMapOf(node1 to node3)
        )

        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() {
    //     dopóki (fałsz) 123
    //     456
    // }

    @Test
    fun `simple loop`() {
        val condition = Expression.BooleanLiteral(false)
        val value1 = Expression.NumberLiteral(123)
        val value2 = Expression.NumberLiteral(456)
        val evaluation1 = Statement.Evaluation(value1)
        val evaluation2 = Statement.Evaluation(value2)
        val loop = Statement.Loop(condition, listOf(evaluation1))
        val function = Function("f", listOf(), Type.Unit, listOf(loop, evaluation2))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node1 = addExpressionNode(condition, null)
        val node2 = addExpressionNode(value1, null)
        val node3 = addExpressionNode(value2, null)

        val result = test(program)

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3),
            node1,
            referenceHashMapOf(node2 to node1),
            referenceHashMapOf(node1 to node2),
            referenceHashMapOf(node1 to node3)
        )

        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() {
    //     dopóki (prawda) {
    //         jeśli (prawda) przerwij
    //         123
    //     }
    //     456
    // }

    @Test
    fun `loop with break`() {
        val condition1 = Expression.BooleanLiteral(true)
        val condition2 = Expression.BooleanLiteral(true)
        val value1 = Expression.NumberLiteral(123)
        val value2 = Expression.NumberLiteral(456)
        val evaluation1 = Statement.Evaluation(value1)
        val evaluation2 = Statement.Evaluation(value2)
        val conditional = Statement.Conditional(condition2, listOf(Statement.LoopBreak()), null)
        val loop = Statement.Loop(condition1, listOf(conditional, evaluation1))
        val function = Function("f", listOf(), Type.Unit, listOf(loop, evaluation2))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node1 = addExpressionNode(condition1, null)
        val node2 = addExpressionNode(condition2, null)
        val node3 = addExpressionNode(value1, null)
        val node4 = addExpressionNode(value2, null)

        val result = test(program)

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3, node4),
            node1,
            referenceHashMapOf(node3 to node1),
            referenceHashMapOf(node1 to node2, node2 to node4),
            referenceHashMapOf(node1 to node4, node2 to node3)
        )

        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() {
    //     dopóki (fałsz) {
    //         jeśli (prawda) pomiń
    //         123
    //     }
    //     456
    // }

    @Test
    fun `loop with continuation`() {
        val condition1 = Expression.BooleanLiteral(false)
        val condition2 = Expression.BooleanLiteral(true)
        val value1 = Expression.NumberLiteral(123)
        val value2 = Expression.NumberLiteral(456)
        val evaluation1 = Statement.Evaluation(value1)
        val evaluation2 = Statement.Evaluation(value2)
        val conditional = Statement.Conditional(condition2, listOf(Statement.LoopContinuation()), null)
        val loop = Statement.Loop(condition1, listOf(conditional, evaluation1))
        val function = Function("f", listOf(), Type.Unit, listOf(loop, evaluation2))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node1 = addExpressionNode(condition1, null)
        val node2 = addExpressionNode(condition2, null)
        val node3 = addExpressionNode(value1, null)
        val node4 = addExpressionNode(value2, null)

        val result = test(program)

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3, node4),
            node1,
            referenceHashMapOf(node3 to node1),
            referenceHashMapOf(node1 to node2, node2 to node1),
            referenceHashMapOf(node1 to node4, node2 to node3)
        )

        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() {
    //     dopóki (fałsz) {
    //         jeśli (fałsz) przerwij
    //         jeśli (prawda) pomiń
    //         123
    //     }
    //     456
    // }

    @Test
    fun `loop with break and continuation`() {
        val condition1 = Expression.BooleanLiteral(false)
        val condition2 = Expression.BooleanLiteral(false)
        val condition3 = Expression.BooleanLiteral(true)
        val value1 = Expression.NumberLiteral(123)
        val value2 = Expression.NumberLiteral(456)
        val evaluation1 = Statement.Evaluation(value1)
        val evaluation2 = Statement.Evaluation(value2)
        val conditional1 = Statement.Conditional(condition2, listOf(Statement.LoopBreak()), null)
        val conditional2 = Statement.Conditional(condition3, listOf(Statement.LoopContinuation()), null)
        val loop = Statement.Loop(condition1, listOf(conditional1, conditional2, evaluation1))
        val function = Function("f", listOf(), Type.Unit, listOf(loop, evaluation2))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node1 = addExpressionNode(condition1, null)
        val node2 = addExpressionNode(condition2, null)
        val node3 = addExpressionNode(condition3, null)
        val node4 = addExpressionNode(value1, null)
        val node5 = addExpressionNode(value2, null)

        val result = test(program)

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3, node4, node5),
            node1,
            referenceHashMapOf(node4 to node1),
            referenceHashMapOf(node1 to node2, node2 to node5, node3 to node1),
            referenceHashMapOf(node1 to node5, node2 to node3, node3 to node4)
        )

        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() {
    //     dopóki (prawda) {
    //         jeśli (fałsz) pomiń
    //         dopóki (prawda) {
    //             jeśli (fałsz) pomiń
    //             jeśli (prawda) przerwij
    //             123
    //         }
    //         jeśli (prawda) przerwij
    //     }
    //     456
    // }

    @Test
    fun `nested loops with breaks and continuations`() {
        val condition1 = Expression.BooleanLiteral(true)
        val condition2 = Expression.BooleanLiteral(false)
        val condition3 = Expression.BooleanLiteral(true)
        val condition4 = Expression.BooleanLiteral(false)
        val condition5 = Expression.BooleanLiteral(true)
        val condition6 = Expression.BooleanLiteral(true)
        val value1 = Expression.NumberLiteral(123)
        val value2 = Expression.NumberLiteral(456)
        val evaluation1 = Statement.Evaluation(value1)
        val evaluation2 = Statement.Evaluation(value2)
        val conditional1 = Statement.Conditional(condition2, listOf(Statement.LoopContinuation()), null)
        val conditional2 = Statement.Conditional(condition4, listOf(Statement.LoopContinuation()), null)
        val conditional3 = Statement.Conditional(condition5, listOf(Statement.LoopBreak()), null)
        val conditional4 = Statement.Conditional(condition6, listOf(Statement.LoopBreak()), null)
        val loop2 = Statement.Loop(condition3, listOf(conditional2, conditional3, evaluation1))
        val loop1 = Statement.Loop(condition1, listOf(conditional1, loop2, conditional4))
        val function = Function("f", listOf(), Type.Unit, listOf(loop1, evaluation2))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node1 = addExpressionNode(condition1, null)
        val node2 = addExpressionNode(condition2, null)
        val node3 = addExpressionNode(condition3, null)
        val node4 = addExpressionNode(condition4, null)
        val node5 = addExpressionNode(condition5, null)
        val node6 = addExpressionNode(value1, null)
        val node7 = addExpressionNode(condition6, null)
        val node8 = addExpressionNode(value2, null)

        val result = test(program)

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3, node4, node5, node6, node7, node8),
            node1,
            referenceHashMapOf(node6 to node3),
            referenceHashMapOf(node1 to node2, node2 to node1, node3 to node4, node4 to node3, node5 to node7, node7 to node8),
            referenceHashMapOf(node1 to node8, node2 to node3, node3 to node7, node4 to node5, node5 to node6, node7 to node1)
        )

        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() {
    //     jeśli (prawda) zakończ
    //     123
    // }

    @Test
    fun `function return`() {
        val condition = Expression.BooleanLiteral(true)
        val value = Expression.NumberLiteral(123)
        val functionReturn = Statement.FunctionReturn(Expression.UnitLiteral())
        val conditional = Statement.Conditional(condition, listOf(functionReturn), null)
        val evaluation = Statement.Evaluation(value)
        val function = Function("f", listOf(), Type.Unit, listOf(conditional, evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node1 = addExpressionNode(condition, null)
        val node2 = addExpressionNode(value, null)

        val result = test(program)

        val cfg = ControlFlowGraph(
            listOf(node1, node2),
            node1,
            referenceHashMapOf(),
            referenceHashMapOf(),
            referenceHashMapOf(node1 to node2)
        )

        assertEquals(referenceHashMapOf(function to cfg), result)
    }

    // czynność f() { przerwij }

    @Test
    fun `break outside of loop`() {
        val loopBreak = Statement.LoopBreak()
        val function = Function("f", listOf(), Type.Unit, listOf(loopBreak))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        test(program)

        assertContentEquals(listOf(ControlFlowDiagnostic.BreakOutsideOfLoop(loopBreak)), diagnostics)
    }

    // czynność f() { pomiń }

    @Test
    fun `continuation outside of loop`() {
        val loopContinuation = Statement.LoopContinuation()
        val function = Function("f", listOf(), Type.Unit, listOf(loopContinuation))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        test(program)

        assertContentEquals(listOf(ControlFlowDiagnostic.ContinuationOutsideOfLoop(loopContinuation)), diagnostics)
    }

    // czynność f() {
    //     zakończ
    //     123
    // }

    @Test
    fun `unreachable statement because of return`() {
        val value = Expression.NumberLiteral(123)
        val functionReturn = Statement.FunctionReturn(Expression.UnitLiteral())
        val evaluation = Statement.Evaluation(value)
        val function = Function("f", listOf(), Type.Unit, listOf(functionReturn, evaluation))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        addExpressionNode(value, null)

        test(program)

        assertContentEquals(listOf(ControlFlowDiagnostic.UnreachableStatement(evaluation)), diagnostics)
    }

    // czynność f() {
    //     dopóki (fałsz) {
    //         przerwij
    //         123
    //     }
    // }

    @Test
    fun `unreachable statement because of break`() {
        val condition = Expression.BooleanLiteral(false)
        val value = Expression.NumberLiteral(123)
        val evaluation = Statement.Evaluation(value)
        val loop = Statement.Loop(condition, listOf(Statement.LoopBreak(), evaluation))
        val function = Function("f", listOf(), Type.Unit, listOf(loop))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        addExpressionNode(condition, null)
        addExpressionNode(value, null)

        test(program)

        assertContentEquals(listOf(ControlFlowDiagnostic.UnreachableStatement(evaluation)), diagnostics)
    }

    // czynność f() {
    //     dopóki (fałsz) {
    //         pomiń
    //         123
    //     }
    // }

    @Test
    fun `unreachable statement because of continuation`() {
        val condition = Expression.BooleanLiteral(false)
        val value = Expression.NumberLiteral(123)
        val evaluation = Statement.Evaluation(value)
        val loop = Statement.Loop(condition, listOf(Statement.LoopContinuation(), evaluation))
        val function = Function("f", listOf(), Type.Unit, listOf(loop))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        addExpressionNode(condition, null)
        addExpressionNode(value, null)

        test(program)

        assertContentEquals(listOf(ControlFlowDiagnostic.UnreachableStatement(evaluation)), diagnostics)
    }
}
