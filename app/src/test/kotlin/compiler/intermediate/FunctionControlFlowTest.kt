package compiler.intermediate

import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.diagnostics.Diagnostic
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.ControlFlowDiagnostic
import compiler.diagnostics.Diagnostics
import compiler.intermediate.generators.FunctionDetailsGenerator
import compiler.intermediate.generators.GeneratorDetailsGenerator
import compiler.utils.Ref
import compiler.utils.keyRefMapOf
import compiler.utils.mutableKeyRefMapOf
import compiler.utils.mutableRefMapOf
import compiler.utils.refMapOf
import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionControlFlowTest {
    private val expressionNodes = mutableKeyRefMapOf<Expression, MutableMap<Ref<Variable?>, Ref<IFTNode>>>()
    private val expressionAccessNodes = mutableKeyRefMapOf<Expression, MutableMap<Ref<Variable?>, Ref<IFTNode>>>()
    private val nameResolution = mutableRefMapOf<AstNode, NamedNode>()
    private val defaultParameterValues = mutableKeyRefMapOf<Function.Parameter, Variable>()
    private val functionReturnedValueVariables = mutableKeyRefMapOf<Function, Variable>()
    private val diagnostics = mutableListOf<Diagnostic>()
    private var genForeachFramePointerAddress: Boolean = false
    private fun addExpressionNode(expression: Expression, variable: Variable?): IFTNode {
        val node = IFTNode.Dummy()
        expressionNodes.putIfAbsent(Ref(expression), mutableRefMapOf())
        expressionNodes[Ref(expression)]!![Ref(variable)] = Ref(node)
        return node
    }

    private fun getExpressionCFG(expression: Expression, variable: Variable?, function: Function, accessNodeConsumer: ((ControlFlowGraph, IFTNode) -> Unit)?): ControlFlowGraph {
        val node = expressionNodes[Ref(expression)]?.get(Ref(variable))
        val nodeList = node?.let { listOf(it.value) } ?: emptyList()
        return ControlFlowGraph(nodeList, node?.value, refMapOf(), refMapOf(), refMapOf()).also {
            if (accessNodeConsumer != null) {
                accessNodeConsumer(it, expressionAccessNodes[Ref(expression)]!![Ref(variable)]!!.value)
            }
        }
    }

    private val dummyGeneratorDetailsGenerator = { it: Function ->
        object : GeneratorDetailsGenerator {

            override fun genInitCall(args: List<IFTNode>): FunctionDetailsGenerator.FunctionCallIntermediateForm {
                val generatorId = IFTNode.DummyCallResult()
                return FunctionDetailsGenerator.FunctionCallIntermediateForm(
                    IFTNode.DummyCall(it.copy(name = it.name + "_init"), args, generatorId, IFTNode.DummyCallResult()).toCfg(),
                    generatorId,
                    null
                )
            }

            override fun genResumeCall(framePointer: IFTNode, savedState: IFTNode): FunctionDetailsGenerator.FunctionCallIntermediateForm {
                val nextValue = IFTNode.DummyCallResult()
                val nextState = IFTNode.DummyCallResult()
                return FunctionDetailsGenerator.FunctionCallIntermediateForm(
                    IFTNode.DummyCall(it.copy(name = it.name + "_resume"), listOf(framePointer, savedState), nextValue, nextState).toCfg(),
                    nextValue,
                    nextState
                )
            }

            override fun genFinalizeCall(framePointer: IFTNode): FunctionDetailsGenerator.FunctionCallIntermediateForm {
                return FunctionDetailsGenerator.FunctionCallIntermediateForm(
                    IFTNode.DummyCall(it.copy(name = it.name + "_finalize"), listOf(framePointer), IFTNode.DummyCallResult(), IFTNode.DummyCallResult()).toCfg(),
                    null,
                    null
                )
            }

            override fun genInit(): ControlFlowGraph {
                throw NotImplementedError()
            }

            override fun genResume(mainBody: ControlFlowGraph): ControlFlowGraph {
                throw NotImplementedError()
            }

            override fun genYield(value: IFTNode): ControlFlowGraph {
                return IFTNode.DummyCall(it.copy(name = it.name + "_yield"), listOf(value), IFTNode.DummyCallResult(), IFTNode.DummyCallResult()).toCfg()
            }

            override fun getNestedForeachFramePointerAddress(foreachLoop: Statement.ForeachLoop): IFTNode? {
                return if (genForeachFramePointerAddress) IFTNode.Dummy(listOf("foreach frame pointer address", it, foreachLoop)) else null
            }

            override fun genFinalize(): ControlFlowGraph {
                throw NotImplementedError()
            }

            override val initFDG: FunctionDetailsGenerator
                get() = throw NotImplementedError()
            override val resumeFDG: FunctionDetailsGenerator
                get() = throw NotImplementedError()
            override val finalizeFDG: FunctionDetailsGenerator
                get() = throw NotImplementedError()

            override fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode {
                throw NotImplementedError()
            }

            override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode {
                throw NotImplementedError()
            }
        }
    }

    private fun test(program: Program) = ControlFlow.createGraphForEachFunction(
        program,
        this::getExpressionCFG,
        nameResolution,
        defaultParameterValues,
        functionReturnedValueVariables,
        object : Diagnostics {
            override fun report(diagnostic: Diagnostic) { diagnostics.add(diagnostic) }
            override fun hasAnyError(): Boolean { throw RuntimeException("This method shouldn't be called") }
        },
        { node, variable, _ -> IFTNode.MemoryWrite(IFTNode.MemoryLabel(variable.name), node) },
        dummyGeneratorDetailsGenerator
    )

    // czynność f() { }

    @Test
    fun `empty function`() {
        val function = Function("f", listOf(), Type.Unit, listOf())
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val result = test(program)

        val cfg = ControlFlowGraph(listOf(), null, refMapOf(), refMapOf(), refMapOf())
        assertEquals(keyRefMapOf(function to cfg), result)
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

        val cfg = ControlFlowGraph(listOf(node), node, refMapOf(), refMapOf(), refMapOf())
        assertEquals(keyRefMapOf(function to cfg), result)
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
            refMapOf(node1 to node2),
            refMapOf(),
            refMapOf()
        )

        assertEquals(keyRefMapOf(function to cfg), result)
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

        val cfg = ControlFlowGraph(listOf(node), node, refMapOf(), refMapOf(), refMapOf())
        assertEquals(keyRefMapOf(function to cfg), result)
    }

    // czynność f() {
    //     czynność g(x: Liczba = 123) { }
    // }

    @Test
    fun `nested function with default parameter value`() {
        val value = Expression.NumberLiteral(123)
        val parameter = Function.Parameter("x", Type.Number, value)
        val parameterVariable = Variable(Variable.Kind.VALUE, "", Type.Number, null)
        defaultParameterValues[Ref(parameter)] = parameterVariable
        val nestedFunction = Function("g", listOf(parameter), Type.Unit, listOf())
        val definition = Statement.FunctionDefinition(nestedFunction)
        val function = Function("f", listOf(), Type.Unit, listOf(definition))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node = addExpressionNode(value, parameterVariable)

        val result = test(program)

        val cfg = ControlFlowGraph(listOf(node), node, refMapOf(), refMapOf(), refMapOf())
        val nestedCfg = ControlFlowGraph(listOf(), null, refMapOf(), refMapOf(), refMapOf())
        assertEquals(keyRefMapOf(function to cfg, nestedFunction to nestedCfg), result)
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
        nameResolution[Ref(assignment)] = Ref(variable)
        val function = Function("f", listOf(), Type.Unit, listOf(definition, assignment))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val node = addExpressionNode(value, variable)

        val result = test(program)

        val cfg = ControlFlowGraph(listOf(node), node, refMapOf(), refMapOf(), refMapOf())
        assertEquals(keyRefMapOf(function to cfg), result)
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

        val cfg = ControlFlowGraph(listOf(node), node, refMapOf(), refMapOf(), refMapOf())
        assertEquals(keyRefMapOf(function to cfg), result)
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
            refMapOf(node2 to node3),
            refMapOf(node1 to node2),
            refMapOf(node1 to node3)
        )

        assertEquals(keyRefMapOf(function to cfg), result)
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
            refMapOf(node2 to node4, node3 to node4),
            refMapOf(node1 to node2),
            refMapOf(node1 to node3)
        )

        assertEquals(keyRefMapOf(function to cfg), result)
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
            refMapOf(node2 to node1),
            refMapOf(node1 to node2),
            refMapOf(node1 to node3)
        )

        assertEquals(keyRefMapOf(function to cfg), result)
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
            refMapOf(node3 to node1),
            refMapOf(node1 to node2, node2 to node4),
            refMapOf(node1 to node4, node2 to node3)
        )

        assertEquals(keyRefMapOf(function to cfg), result)
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
            refMapOf(node3 to node1),
            refMapOf(node1 to node2, node2 to node1),
            refMapOf(node1 to node4, node2 to node3)
        )

        assertEquals(keyRefMapOf(function to cfg), result)
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
            refMapOf(node4 to node1),
            refMapOf(node1 to node2, node2 to node5, node3 to node1),
            refMapOf(node1 to node5, node2 to node3, node3 to node4)
        )

        assertEquals(keyRefMapOf(function to cfg), result)
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
            refMapOf(node6 to node3),
            refMapOf(node1 to node2, node2 to node1, node3 to node4, node4 to node3, node5 to node7, node7 to node8),
            refMapOf(node1 to node8, node2 to node3, node3 to node7, node4 to node5, node5 to node6, node7 to node1)
        )

        assertEquals(keyRefMapOf(function to cfg), result)
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
            refMapOf(),
            refMapOf(),
            refMapOf(node1 to node2)
        )

        assertEquals(keyRefMapOf(function to cfg), result)
    }

    // czynność f() { przerwij }

    @Test
    fun `break outside of loop`() {
        val loopBreak = Statement.LoopBreak()
        val function = Function("f", listOf(), Type.Unit, listOf(loopBreak))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        test(program)

        assertEquals(listOf<Diagnostic>(ControlFlowDiagnostic.Errors.BreakOutsideOfLoop(loopBreak)), diagnostics)
    }

    // czynność f() { pomiń }

    @Test
    fun `continuation outside of loop`() {
        val loopContinuation = Statement.LoopContinuation()
        val function = Function("f", listOf(), Type.Unit, listOf(loopContinuation))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        test(program)

        assertEquals(listOf<Diagnostic>(ControlFlowDiagnostic.Errors.ContinuationOutsideOfLoop(loopContinuation)), diagnostics)
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

        assertEquals(listOf<Diagnostic>(ControlFlowDiagnostic.Warnings.UnreachableStatement(evaluation)), diagnostics)
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

        assertEquals(listOf<Diagnostic>(ControlFlowDiagnostic.Warnings.UnreachableStatement(evaluation)), diagnostics)
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

        assertEquals(listOf<Diagnostic>(ControlFlowDiagnostic.Warnings.UnreachableStatement(evaluation)), diagnostics)
    }

    @Test
    fun `simple foreach`() {
        val generator = Function(
            "generator",
            emptyList(),
            Type.Unit,
            Function.Implementation.Local(emptyList()),
            true
        )
        val generatorInit = generator.copy(name = "generator_init")
        val generatorResume = generator.copy(name = "generator_resume")
        val generatorFinalize = generator.copy(name = "generator_finalize")

        val genCall = Expression.FunctionCall("generator", emptyList(), null)
        val genCallResult = dummyGeneratorDetailsGenerator(generator).genInitCall(emptyList())

        expressionNodes[Ref(genCall)] = mutableMapOf(Ref(null) to Ref(genCallResult.callGraph.entryTreeRoot!!))
        expressionAccessNodes[Ref(genCall)] = mutableMapOf(Ref(null) to Ref(genCallResult.result!!))
        nameResolution[Ref(genCall)] = Ref(generator)

        val variable = Variable(
            Variable.Kind.VARIABLE,
            "x",
            Type.Number,
            null
        )
        val value = Expression.NumberLiteral(123)
        expressionNodes[Ref(value)] = mutableMapOf(Ref(null) to Ref(IFTNode.Const(123)))
        val foreach = Statement.ForeachLoop(variable, genCall, listOf(Statement.Evaluation(value)))

        val function = Function("f", listOf(), Type.Unit, listOf(foreach))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val result = test(program)
        val resultCfg = result.values.first()

        val idRegister = Register()
        val stateRegister = Register()
        val generatorId = IFTNode.DummyCallResult()
        val stateResult = IFTNode.DummyCallResult()
        val valueResult = IFTNode.DummyCallResult()
        val idRegisterRead = IFTNode.RegisterRead(idRegister)

        val expectedCfg = (
            IFTNode.DummyCall(generatorInit, emptyList(), generatorId, IFTNode.DummyCallResult())
                merge IFTNode.RegisterWrite(idRegister, generatorId)
                merge IFTNode.RegisterWrite(stateRegister, IFTNode.Const(0))
                merge mergeCFGsInLoop(
                    IFTNode.DummyCall(generatorResume, listOf(idRegisterRead, IFTNode.RegisterRead(stateRegister)), valueResult, stateResult)
                        merge IFTNode.NotEquals(stateResult, IFTNode.Const(0)),
                    IFTNode.RegisterWrite(stateRegister, stateResult)
                        merge IFTNode.MemoryWrite(IFTNode.MemoryLabel(variable.name), valueResult)
                        merge IFTNode.Const(FixedConstant(123)),
                    IFTNode.DummyCall(generatorFinalize, listOf(idRegisterRead), IFTNode.DummyCallResult(), IFTNode.DummyCallResult()).toCfg()
                )
            )
        resultCfg hasSameStructureAs expectedCfg
    }

    @Test
    fun `nested foreach`() {
        genForeachFramePointerAddress = true

        val generator = Function(
            "generator",
            emptyList(),
            Type.Unit,
            Function.Implementation.Local(emptyList()),
            true
        )
        val generatorInit = generator.copy(name = "generator_init")
        val generatorResume = generator.copy(name = "generator_resume")
        val generatorFinalize = generator.copy(name = "generator_finalize")

        val genCall = Expression.FunctionCall("generator", emptyList(), null)
        val genCallResult = dummyGeneratorDetailsGenerator(generator).genInitCall(emptyList())

        expressionNodes[Ref(genCall)] = mutableMapOf(Ref(null) to Ref(genCallResult.callGraph.entryTreeRoot!!))
        expressionAccessNodes[Ref(genCall)] = mutableMapOf(Ref(null) to Ref(genCallResult.result!!))
        nameResolution[Ref(genCall)] = Ref(generator)

        val variable = Variable(
            Variable.Kind.VARIABLE,
            "x",
            Type.Number,
            null
        )
        val value = Expression.NumberLiteral(123)
        expressionNodes[Ref(value)] = mutableMapOf(Ref(null) to Ref(IFTNode.Const(123)))
        val foreach = Statement.ForeachLoop(variable, genCall, listOf(Statement.Evaluation(value)))

        val function = Function("f", listOf(), Type.Unit, listOf(foreach), isGenerator = true)
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val result = test(program)
        val resultCfg = result.values.first()

        val stateRegister = Register()
        val frameMemoryAddress = IFTNode.Dummy(listOf("foreach frame pointer address", function, foreach))
        val generatorId = IFTNode.DummyCallResult()
        val stateResult = IFTNode.DummyCallResult()
        val valueResult = IFTNode.DummyCallResult()
        val idRegisterRead = IFTNode.MemoryRead(frameMemoryAddress)

        val expectedCfg = (
            IFTNode.DummyCall(generatorInit, emptyList(), generatorId, IFTNode.DummyCallResult())
                merge IFTNode.MemoryWrite(frameMemoryAddress, generatorId)
                merge IFTNode.RegisterWrite(stateRegister, IFTNode.Const(0))
                merge mergeCFGsInLoop(
                    IFTNode.DummyCall(generatorResume, listOf(idRegisterRead, IFTNode.RegisterRead(stateRegister)), valueResult, stateResult)
                        merge IFTNode.NotEquals(stateResult, IFTNode.Const(0)),
                    IFTNode.RegisterWrite(stateRegister, stateResult)
                        merge IFTNode.MemoryWrite(IFTNode.MemoryLabel(variable.name), valueResult)
                        merge IFTNode.Const(FixedConstant(123)),
                    IFTNode.DummyCall(generatorFinalize, listOf(idRegisterRead), IFTNode.DummyCallResult(), IFTNode.DummyCallResult())
                        merge IFTNode.MemoryWrite(frameMemoryAddress, IFTNode.Const(0))
                )
            )
        resultCfg hasSameStructureAs expectedCfg
    }
    @Test
    fun `foreach - break, continue, return`() {
        val generator = Function(
            "generator",
            emptyList(),
            Type.Unit,
            Function.Implementation.Local(emptyList()),
            true
        )
        val generatorInit = generator.copy(name = "generator_init")
        val generatorResume = generator.copy(name = "generator_resume")
        val generatorFinalize = generator.copy(name = "generator_finalize")

        val genCall = Expression.FunctionCall("generator", emptyList(), null)
        val genCallResult = dummyGeneratorDetailsGenerator(generator).genInitCall(emptyList())

        expressionNodes[Ref(genCall)] = mutableMapOf(Ref(null) to Ref(genCallResult.callGraph.entryTreeRoot!!))
        expressionAccessNodes[Ref(genCall)] = mutableMapOf(Ref(null) to Ref(genCallResult.result!!))
        nameResolution[Ref(genCall)] = Ref(generator)

        val variable = Variable(
            Variable.Kind.VARIABLE,
            "x",
            Type.Number,
            null
        )
        val value1 = Expression.NumberLiteral(123)
        val value2 = Expression.NumberLiteral(123)
        val value3 = Expression.NumberLiteral(123)
        val trueLiteral1 = Expression.BooleanLiteral(true)
        val trueLiteral2 = Expression.BooleanLiteral(true)
        val unitLiteral = Expression.UnitLiteral()
        expressionNodes[Ref(value1)] = mutableMapOf(Ref(null) to Ref(IFTNode.Const(123)))
        expressionNodes[Ref(value2)] = mutableMapOf(Ref(null) to Ref(IFTNode.Const(123)))
        expressionNodes[Ref(value3)] = mutableMapOf(Ref(null) to Ref(IFTNode.Const(123)))
        expressionNodes[Ref(trueLiteral1)] = mutableMapOf(Ref(null) to Ref(IFTNode.Const(1)))
        expressionNodes[Ref(trueLiteral2)] = mutableMapOf(Ref(null) to Ref(IFTNode.Const(1)))
        expressionNodes[Ref(unitLiteral)] = mutableMapOf(Ref(null) to Ref(IFTNode.Const(0)))
        val foreach = Statement.ForeachLoop(
            variable,
            genCall,
            listOf(
                Statement.Conditional(
                    trueLiteral1,
                    listOf(
                        Statement.Evaluation(value1),
                        Statement.LoopBreak(),
                    ),
                    listOf(
                        Statement.Conditional(
                            trueLiteral2,
                            listOf(
                                Statement.Evaluation(value2),
                                Statement.LoopContinuation(),
                            ),
                            listOf(
                                Statement.Evaluation(value3),
                                Statement.FunctionReturn(unitLiteral)
                            )
                        )
                    )
                )
            )
        )

        val function = Function("f", listOf(), Type.Unit, listOf(foreach))
        val program = Program(listOf(Program.Global.FunctionDefinition(function)))

        val result = test(program)
        val resultCfg = result.values.first()

        val idRegister = Register()
        val stateRegister = Register()
        val generatorId = IFTNode.DummyCallResult()
        val stateResult = IFTNode.DummyCallResult()
        val valueResult = IFTNode.DummyCallResult()
        val idRegisterRead = IFTNode.RegisterRead(idRegister)

        val expectedCfg = (
            IFTNode.DummyCall(generatorInit, emptyList(), generatorId, IFTNode.DummyCallResult())
                merge IFTNode.RegisterWrite(idRegister, generatorId)
                merge IFTNode.RegisterWrite(stateRegister, IFTNode.Const(0))
                merge mergeCFGsInLoop(
                    IFTNode.DummyCall(generatorResume, listOf(idRegisterRead, IFTNode.RegisterRead(stateRegister)), valueResult, stateResult)
                        merge IFTNode.NotEquals(stateResult, IFTNode.Const(0)),
                    IFTNode.RegisterWrite(stateRegister, stateResult)
                        merge IFTNode.MemoryWrite(IFTNode.MemoryLabel(variable.name), valueResult)
                        merge mergeCFGsConditionally(
                            IFTNode.Const(1).toCfg(),
                            IFTNode.Const(123).toCfg(), // 8
                            mergeCFGsConditionally(
                                IFTNode.Const(1).toCfg(),
                                IFTNode.Const(123).toCfg(),
                                IFTNode.Const(123)
                                    merge IFTNode.Const(0)
                                    merge IFTNode.DummyCall(generatorFinalize, listOf(idRegisterRead), IFTNode.DummyCallResult(), IFTNode.DummyCallResult()).toCfg()
                            )
                        ),
                    IFTNode.DummyCall(generatorFinalize, listOf(idRegisterRead), IFTNode.DummyCallResult(), IFTNode.DummyCallResult()).toCfg()
                )
            ).let {
            val builder = ControlFlowGraphBuilder()
            builder.addAllFrom(it)
            builder.addLink(Pair(it.treeRoots[8], CFGLinkType.UNCONDITIONAL), it.treeRoots[14]) // break
            builder.addLink(Pair(it.treeRoots[10], CFGLinkType.UNCONDITIONAL), it.treeRoots[3]) // continue
            builder.build()
        }
        resultCfg hasSameStructureAs expectedCfg
    }

    @Test
    fun `yield test`() {

        val value = Expression.NumberLiteral(123)
        expressionAccessNodes[Ref(value)] = mutableMapOf(Ref(null) to Ref(IFTNode.Const(123)))

        val generator = Function(
            "generator",
            emptyList(),
            Type.Unit,
            Function.Implementation.Local(listOf(Statement.GeneratorYield(value))),
            true
        )
        val program = Program(listOf(Program.Global.FunctionDefinition(generator)))
        val result = test(program)
        val resultCfg = result.values.first()

        resultCfg hasSameStructureAs IFTNode.DummyCall(generator.copy(name = generator.name + "_yield"), listOf(IFTNode.Const(123)), IFTNode.DummyCallResult()).toCfg()
    }
}
