package compiler.intermediate

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Statement
import compiler.ast.Type
import compiler.intermediate.generators.ArrayMemoryManagement
import compiler.intermediate.generators.FunctionDetailsGenerator
import compiler.intermediate.generators.GeneratorDetailsGenerator
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

// CFG comparison tools

infix fun ControlFlowGraph.assertHasSameStructureAs(cfg: ControlFlowGraph) {
    val registersMap = mutableKeyRefMapOf<Register, Register>()
    val callResultsMap = mutableKeyRefMapOf<IFTNode.DummyCallResult, IFTNode.DummyCallResult>()
    val nodeMap = mutableKeyRefMapOf<IFTNode, IFTNode>()

    fun <T> MutableMap<Ref<T>, T>.ensurePairSymmetrical(a: T, b: T) {
        if (!this.containsKey(Ref(a))) {
            this[Ref(a)] = b
        }
        assertSame(this[Ref(a)]!!, b)
    }

    infix fun IFTNode.assertHasSameStructureAs(iftNode: IFTNode) {

        assertEquals(this::class, iftNode::class)
        nodeMap.ensurePairSymmetrical(this, iftNode)
        when (this) {
            is IFTNode.BinaryOperator -> {
                this.left assertHasSameStructureAs (iftNode as IFTNode.BinaryOperator).left
                this.right assertHasSameStructureAs iftNode.right
            }

            is IFTNode.UnaryOperator -> {
                this.node assertHasSameStructureAs (iftNode as IFTNode.UnaryOperator).node
            }

            is IFTNode.DummyCall -> {
                assertEquals(this.function, (iftNode as IFTNode.DummyCall).function)
                assertEquals(this.args.size, iftNode.args.size)
                (this.args zip iftNode.args).forEach {
                    nodeMap.ensurePairSymmetrical(it.first, it.second)
                }
                this.callResult1 assertHasSameStructureAs iftNode.callResult1
                this.callResult2 assertHasSameStructureAs iftNode.callResult2
            }

            is IFTNode.DummyCallResult -> callResultsMap.ensurePairSymmetrical(this, iftNode as IFTNode.DummyCallResult)
            is IFTNode.DummyWrite -> {
                assertTrue((this.namedNode == (iftNode as IFTNode.DummyWrite).namedNode) && (this.isDirect == iftNode.isDirect) && (this.isGlobal == iftNode.isGlobal))
                nodeMap.ensurePairSymmetrical(this.value, iftNode.value)
            }
            is IFTNode.MemoryWrite -> {
                assertEquals(this.address, (iftNode as IFTNode.MemoryWrite).address)
                this.value assertHasSameStructureAs iftNode.value
            }
            is IFTNode.MemoryRead -> {
                this.address assertHasSameStructureAs (iftNode as IFTNode.MemoryRead).address
            }
            is IFTNode.RegisterWrite -> {
                registersMap.ensurePairSymmetrical(this.register, (iftNode as IFTNode.RegisterWrite).register)
                this.node assertHasSameStructureAs iftNode.node
            }
            is IFTNode.RegisterRead -> registersMap.ensurePairSymmetrical(this.register, (iftNode as IFTNode.RegisterRead).register)
            is IFTNode.DummyArrayAllocation -> {
                assertEquals(this.size, (iftNode as IFTNode.DummyArrayAllocation).size)
                assertEquals(this.type, iftNode.type)
                assertEquals(this.initList.size, iftNode.initList.size)
                (this.initList zip iftNode.initList).forEach { it.first assertHasSameStructureAs it.second }
            }
            is IFTNode.DummyArrayRefCountInc -> this.address assertHasSameStructureAs (iftNode as IFTNode.DummyArrayRefCountInc).address
            is IFTNode.DummyArrayRefCountDec -> {
                assertEquals(this.type, (iftNode as IFTNode.DummyArrayRefCountDec).type)
                this.address assertHasSameStructureAs iftNode.address
            }
            is IFTNode.Dummy -> assertEquals(this.info, (iftNode as IFTNode.Dummy).info)
            else -> {
                assertEquals(this, iftNode)
            }
        }
    }

    assertEquals(this.treeRoots.size, cfg.treeRoots.size)

    fun dfs(left: Ref<IFTNode>, right: Ref<IFTNode>) {
        left.value assertHasSameStructureAs right.value

        if (this.unconditionalLinks.containsKey(left)) {
            assertTrue(cfg.unconditionalLinks.containsKey(right))
            val leftNext = this.unconditionalLinks[left]!!
            val rightNext = cfg.unconditionalLinks[right]!!
            if (nodeMap.containsKey(leftNext)) {
                assertSame(nodeMap[leftNext]!!, rightNext.value)
            } else {
                dfs(leftNext, rightNext)
            }
        }

        if (this.conditionalTrueLinks.containsKey(left)) {
            assertTrue(cfg.conditionalTrueLinks.containsKey(right))
            val leftNext = this.conditionalTrueLinks[left]!!
            val rightNext = cfg.conditionalTrueLinks[right]!!
            if (nodeMap.containsKey(leftNext)) {
                assertSame(nodeMap[leftNext]!!, rightNext.value)
            } else {
                dfs(leftNext, rightNext)
            }
        }

        if (this.conditionalFalseLinks.containsKey(left)) {
            assertTrue(cfg.conditionalFalseLinks.containsKey(right))
            val leftNext = this.conditionalFalseLinks[left]!!
            val rightNext = cfg.conditionalFalseLinks[right]!!
            if (nodeMap.containsKey(leftNext)) {
                assertSame(nodeMap[leftNext]!!, rightNext.value)
            } else {
                dfs(leftNext, rightNext)
            }
        }
    }

    if (this.entryTreeRoot == null)
        assertEquals(cfg.entryTreeRoot, null)
    else
        dfs(this.entryTreeRoot!!, cfg.entryTreeRoot!!)
}

// CFG construction tools

fun IFTNode.toCfg(): ControlFlowGraph =
    ControlFlowGraphBuilder().addSingleTree(this).build()

infix fun ControlFlowGraph.merge(cfg: ControlFlowGraph): ControlFlowGraph =
    ControlFlowGraphBuilder().mergeUnconditionally(this).mergeUnconditionally(cfg).build()

infix fun IFTNode.merge(cfg: ControlFlowGraph): ControlFlowGraph =
    this.toCfg() merge cfg

infix fun ControlFlowGraph.merge(iftNode: IFTNode): ControlFlowGraph =
    this merge iftNode.toCfg()

infix fun IFTNode.merge(iftNode: IFTNode): ControlFlowGraph =
    this.toCfg() merge iftNode.toCfg()

infix fun ControlFlowGraph.add(cfg: ControlFlowGraph) =
    ControlFlowGraphBuilder().mergeUnconditionally(this).addAllFrom(cfg).build()

fun mergeCFGsConditionally(condition: ControlFlowGraph, cfgTrue: ControlFlowGraph, cfgFalse: ControlFlowGraph): ControlFlowGraph {
    return ControlFlowGraphBuilder().mergeUnconditionally(condition).mergeConditionally(cfgTrue, cfgFalse).build()
}

fun mergeCFGsInLoop(condition: ControlFlowGraph, cfgLoop: ControlFlowGraph, cfgEnd: ControlFlowGraph): ControlFlowGraph {
    return ControlFlowGraphBuilder().apply {
        addAllFrom(condition)
        addAllFrom(cfgLoop)
        addAllFrom(cfgEnd)
        condition.finalTreeRoots.forEach {
            addLink(Pair(it.first, CFGLinkType.CONDITIONAL_TRUE), cfgLoop.entryTreeRoot!!)
            addLink(Pair(it.first, CFGLinkType.CONDITIONAL_FALSE), cfgEnd.entryTreeRoot!!)
        }
        cfgLoop.finalTreeRoots.forEach {
            addLink(Pair(it.first, CFGLinkType.UNCONDITIONAL), condition.entryTreeRoot!!)
        }
    }.build()
}

// dummy ArrayMemoryManagement implementation

fun dummyArrayAddress(id: Int) = IFTNode.Dummy("address of array $id")

class TestArrayMemoryManagement : ArrayMemoryManagement {
    private var arrayId = 0
    override fun genAllocation(size: IFTNode, initialization: List<IFTNode>, type: Type, mode: Expression.ArrayAllocation.InitializationType): Pair<ControlFlowGraph, IFTNode> =
        Pair(
            IFTNode.DummyArrayAllocation(size, initialization, type, mode).toCfg(),
            dummyArrayAddress(arrayId++)
        )

    override fun genRefCountIncrement(address: IFTNode): ControlFlowGraph = IFTNode.DummyArrayRefCountInc(address).toCfg()

    override fun genRefCountDecrement(address: IFTNode, type: Type): ControlFlowGraph = IFTNode.DummyArrayRefCountDec(address, type).toCfg()
}

// dummy Function- and GeneratorDetailsGenerator

class TestFunctionDetailsGenerator(val function: Function) : FunctionDetailsGenerator {
    override fun genCall(args: List<IFTNode>): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        val callResult = IFTNode.DummyCallResult()
        return FunctionDetailsGenerator.FunctionCallIntermediateForm(
            ControlFlowGraphBuilder().addSingleTree(IFTNode.DummyCall(function, args, callResult)).build(),
            callResult,
            null
        )
    }

    override fun genPrologue(): ControlFlowGraph {
        throw NotImplementedError()
    }

    override fun genEpilogue(): ControlFlowGraph {
        throw NotImplementedError()
    }

    override val spilledRegistersRegionOffset get() = throw NotImplementedError()
    override val spilledRegistersRegionSize get() = throw NotImplementedError()
    override val identifier: String get() = function.name

    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode {
        return IFTNode.DummyRead(namedNode, isDirect)
    }

    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode {
        return IFTNode.DummyWrite(namedNode, value, isDirect)
    }
}

class TestGeneratorDetailsGenerator(
    private val function: Function,
    private val genForeachFramePointerAddress: Boolean
) : GeneratorDetailsGenerator {
    override fun genInitCall(args: List<IFTNode>): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        val generatorId = IFTNode.DummyCallResult()
        return FunctionDetailsGenerator.FunctionCallIntermediateForm(
            IFTNode.DummyCall(function.copy(name = function.name + "_init"), args, generatorId, IFTNode.DummyCallResult())
                .toCfg(),
            generatorId,
            null
        )
    }

    override fun genResumeCall(
        framePointer: IFTNode,
        savedState: IFTNode
    ): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        val nextValue = IFTNode.DummyCallResult()
        val nextState = IFTNode.DummyCallResult()
        return FunctionDetailsGenerator.FunctionCallIntermediateForm(
            IFTNode.DummyCall(
                function.copy(name = function.name + "_resume"),
                listOf(framePointer, savedState),
                nextValue,
                nextState
            ).toCfg(),
            nextValue,
            nextState
        )
    }

    override fun genFinalizeCall(framePointer: IFTNode): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        return FunctionDetailsGenerator.FunctionCallIntermediateForm(
            IFTNode.DummyCall(
                function.copy(name = function.name + "_finalize"),
                listOf(framePointer),
                IFTNode.DummyCallResult(),
                IFTNode.DummyCallResult()
            ).toCfg(),
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
        return IFTNode.DummyCall(
            function.copy(name = function.name + "_yield"),
            listOf(value),
            IFTNode.DummyCallResult(),
            IFTNode.DummyCallResult()
        ).toCfg()
    }

    override fun getNestedForeachFramePointerAddress(foreachLoop: Statement.ForeachLoop): IFTNode? {
        return if (genForeachFramePointerAddress) IFTNode.Dummy(
            listOf(
                "foreach frame pointer address",
                function,
                foreachLoop
            )
        ) else null
    }

    override fun genFinalize(): ControlFlowGraph {
        throw NotImplementedError()
    }

    override val initFDG = mockk<FunctionDetailsGenerator>().also { every { it.identifier } returns "${function.name}_init" }
    override val resumeFDG = mockk<FunctionDetailsGenerator>().also { every { it.identifier } returns "${function.name}_resume" }
    override val finalizeFDG = mockk<FunctionDetailsGenerator>().also { every { it.identifier } returns "${function.name}_finalize" }

    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode {
        throw NotImplementedError()
    }

    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode {
        throw NotImplementedError()
    }
}
