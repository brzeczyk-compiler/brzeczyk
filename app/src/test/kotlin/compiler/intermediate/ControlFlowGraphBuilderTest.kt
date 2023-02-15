package compiler.intermediate

import compiler.utils.Ref
import compiler.utils.refMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ControlFlowGraphBuilderTest {
    private val regToRead = Register()
    private val regToWrite = Register()

    private val innerNode = IFTNode.RegisterRead(regToRead)

    private val entryNode = IFTNode.RegisterWrite(regToWrite, innerNode)
    private val secondNode = IFTNode.Dummy()
    private val conditionalTrueNode = IFTNode.Dummy()
    private val conditionalFalseNode = IFTNode.Dummy()

    private val simpleCFG = ControlFlowGraph(
        entryTreeRoot = Ref(entryNode),
        unconditionalLinks = refMapOf(entryNode to secondNode),
        conditionalTrueLinks = refMapOf(secondNode to conditionalTrueNode),
        conditionalFalseLinks = refMapOf(secondNode to conditionalFalseNode)
    )

    @Test
    fun `test pass entryTreeRoot in constructor`() {
        val cfg = ControlFlowGraphBuilder(entryNode).build()

        assertSame(cfg.entryTreeRoot?.value, entryNode)
        assertEquals(cfg.unconditionalLinks, emptyMap())
        assertEquals(cfg.conditionalFalseLinks, emptyMap())
        assertEquals(cfg.conditionalTrueLinks, emptyMap())
    }

    @Test
    fun `test addLink`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addLink(null, Ref(entryNode))
        cfgBuilder.addLink(Pair(Ref(entryNode), CFGLinkType.UNCONDITIONAL), Ref(secondNode))
        cfgBuilder.addLink(Pair(Ref(secondNode), CFGLinkType.CONDITIONAL_TRUE), Ref(conditionalTrueNode))
        cfgBuilder.addLink(Pair(Ref(secondNode), CFGLinkType.CONDITIONAL_FALSE), Ref(conditionalFalseNode))

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }

    @Test
    fun `test addAllFrom`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addAllFrom(simpleCFG)

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }

    @Test
    fun `test addLinkFromAllFinalRoots when no entryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, entryNode)
        assertEquals(ControlFlowGraphBuilder(entryNode).build(), cfgBuilder.build())
    }

    @Test
    fun `test addLinkFromAllFinalRoots`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, secondNode)
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.CONDITIONAL_TRUE, conditionalTrueNode)
        cfgBuilder.addLink(Pair(Ref(secondNode), CFGLinkType.CONDITIONAL_FALSE), Ref(conditionalFalseNode))
        assertEquals(simpleCFG, cfgBuilder.build())
    }

    @Test
    fun `test addAllFrom without modifying entryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        val remainingThreeNodes = ControlFlowGraphBuilder(secondNode)
        remainingThreeNodes.addLink(Pair(Ref(secondNode), CFGLinkType.CONDITIONAL_TRUE), Ref(conditionalTrueNode))
        remainingThreeNodes.addLink(Pair(Ref(secondNode), CFGLinkType.CONDITIONAL_FALSE), Ref(conditionalFalseNode))
        cfgBuilder.addAllFrom(remainingThreeNodes.build())
        cfgBuilder.addLink(Pair(Ref(entryNode), CFGLinkType.UNCONDITIONAL), Ref(secondNode))

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }

    @Test
    fun `test mergeUnconditionally`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)

        val remaining = ControlFlowGraphBuilder(secondNode)
        remaining.addLink(Pair(Ref(secondNode), CFGLinkType.CONDITIONAL_TRUE), Ref(conditionalTrueNode))
        remaining.addLink(Pair(Ref(secondNode), CFGLinkType.CONDITIONAL_FALSE), Ref(conditionalFalseNode))
        cfgBuilder.mergeUnconditionally(remaining.build())

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }

    @Test
    fun `test mergeConditionally`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        cfgBuilder.addLink(Pair(Ref(entryNode), CFGLinkType.UNCONDITIONAL), Ref(secondNode))

        cfgBuilder.mergeConditionally(
            ControlFlowGraphBuilder(conditionalTrueNode).build(),
            ControlFlowGraphBuilder(conditionalFalseNode).build()
        )

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }

    @Test
    fun `test addSingleTree`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        cfgBuilder.addSingleTree(secondNode)
        cfgBuilder.mergeConditionally(
            ControlFlowGraphBuilder(conditionalTrueNode).build(),
            ControlFlowGraphBuilder(conditionalFalseNode).build()
        )

        val cfg = cfgBuilder.build()
        assertEquals(simpleCFG, cfg)
    }
}
