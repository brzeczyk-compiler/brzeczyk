package compiler.intermediate_form
import compiler.common.reference_collections.referenceHashMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ControlFlowGraphBuilderTest {
    private val regRead = Register()
    private val regWrite = Register()

    private val innerNode = IntermediateFormTreeNode.RegisterRead(regRead)

    private val entryNode = IntermediateFormTreeNode.RegisterWrite(regWrite, innerNode)
    private val secondNode = IntermediateFormTreeNode.NoOp()
    private val conditionalTrueNode = IntermediateFormTreeNode.NoOp()
    private val conditionalFalseNode = IntermediateFormTreeNode.NoOp()

    private val expectedCFG = ControlFlowGraph(
        treeRoots = listOf(entryNode, secondNode, conditionalTrueNode, conditionalFalseNode),
        entryTreeRoot = entryNode,
        unconditionalLinks = referenceHashMapOf(entryNode to secondNode),
        conditionalTrueLinks = referenceHashMapOf(secondNode to conditionalTrueNode),
        conditionalFalseLinks = referenceHashMapOf(secondNode to conditionalFalseNode)
    )

    @Test
    fun `test pass entryTreeRoot in constructor`() {
        val cfg = ControlFlowGraphBuilder(entryNode).build()

        assertEquals(cfg.entryTreeRoot, entryNode)
        assertEquals(cfg.treeRoots, listOf(entryNode))
        assertEquals(cfg.unconditionalLinks, referenceHashMapOf())
        assertEquals(cfg.conditionalFalseLinks, referenceHashMapOf())
        assertEquals(cfg.conditionalTrueLinks, referenceHashMapOf())
    }

    @Test
    fun `test setEntryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.setEntryTreeRoot(entryNode)

        val cfg = cfgBuilder.build()
        assertEquals(ControlFlowGraphBuilder(entryNode).build(), cfg)
    }

    @Test
    fun `test setEntryTreeRoot when already specified`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        assertFailsWith<IncorrectControlFlowGraphError> { cfgBuilder.setEntryTreeRoot(entryNode) }
    }

    @Test
    fun `test addLink from null sets entryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addLink(null, entryNode)
        cfgBuilder.addLink(Pair(entryNode, CFGLinkType.UNCONDITIONAL), secondNode)
        cfgBuilder.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_TRUE), conditionalTrueNode)
        cfgBuilder.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_FALSE), conditionalFalseNode)

        val cfg = cfgBuilder.build()
        assertEquals(expectedCFG, cfg)
    }

    @Test
    fun `test addLinksFromAllFinalRoots`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, secondNode)
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.CONDITIONAL_TRUE, conditionalTrueNode)
        cfgBuilder.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_FALSE), conditionalFalseNode)

        val cfg = cfgBuilder.build()
        assertEquals(expectedCFG, cfg)
    }

    @Test
    fun `test addLinksFromAllFinalRoots when no entryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, entryNode)
        assertEquals(ControlFlowGraphBuilder(entryNode).build(), cfgBuilder.build())
    }

    @Test
    fun `test addAllFrom`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addAllFrom(expectedCFG)

        val cfg = cfgBuilder.build()
        assertEquals(expectedCFG, cfg)
    }

    @Test
    fun `test addAllFrom without modifying entryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)

        val remainingTwoNodes = ControlFlowGraphBuilder(secondNode)
        remainingTwoNodes.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_TRUE), conditionalTrueNode)
        remainingTwoNodes.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_FALSE), conditionalFalseNode)
        cfgBuilder.addAllFrom(remainingTwoNodes.build())
        cfgBuilder.addLink(Pair(entryNode, CFGLinkType.UNCONDITIONAL), secondNode)

        val cfg = cfgBuilder.build()
        assertEquals(expectedCFG, cfg)
    }

    @Test
    fun `test mergeUnconditionally`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)

        val remaining = ControlFlowGraphBuilder(secondNode)
        remaining.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_TRUE), conditionalTrueNode)
        remaining.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_FALSE), conditionalFalseNode)
        cfgBuilder.mergeUnconditionally(remaining.build())

        val cfg = cfgBuilder.build()
        assertEquals(expectedCFG, cfg)
    }

    @Test
    fun `test mergeConditionally`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        cfgBuilder.addLink(Pair(entryNode, CFGLinkType.UNCONDITIONAL), secondNode)

        cfgBuilder.mergeConditionally(
            ControlFlowGraphBuilder(conditionalTrueNode).build(),
            ControlFlowGraphBuilder(conditionalFalseNode).build()
        )

        val cfg = cfgBuilder.build()
        assertEquals(expectedCFG, cfg)
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
        assertEquals(expectedCFG, cfg)
    }
}
