package compiler.intermediate

import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    fun dfs(left: IFTNode, right: IFTNode) {
        left assertHasSameStructureAs right

        if (this.unconditionalLinks.containsKey(Ref(left))) {
            assertTrue(cfg.unconditionalLinks.containsKey(Ref(right)))
            val leftNext = this.unconditionalLinks[Ref(left)]!!.value
            val rightNext = cfg.unconditionalLinks[Ref(right)]!!.value
            if (nodeMap.containsKey(Ref(leftNext))) {
                assertSame(nodeMap[Ref(leftNext)]!!, rightNext)
            } else {
                dfs(leftNext, rightNext)
            }
        }

        if (this.conditionalTrueLinks.containsKey(Ref(left))) {
            assertTrue(cfg.conditionalTrueLinks.containsKey(Ref(right)))
            val leftNext = this.conditionalTrueLinks[Ref(left)]!!.value
            val rightNext = cfg.conditionalTrueLinks[Ref(right)]!!.value
            if (nodeMap.containsKey(Ref(leftNext))) {
                assertSame(nodeMap[Ref(leftNext)]!!, rightNext)
            } else {
                dfs(leftNext, rightNext)
            }
        }

        if (this.conditionalFalseLinks.containsKey(Ref(left))) {
            assertTrue(cfg.conditionalFalseLinks.containsKey(Ref(right)))
            val leftNext = this.conditionalFalseLinks[Ref(left)]!!.value
            val rightNext = cfg.conditionalFalseLinks[Ref(right)]!!.value
            if (nodeMap.containsKey(Ref(leftNext))) {
                assertSame(nodeMap[Ref(leftNext)]!!, rightNext)
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
