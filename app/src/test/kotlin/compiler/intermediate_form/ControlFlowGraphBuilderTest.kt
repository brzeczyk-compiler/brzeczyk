package compiler.intermediate_form
import compiler.common.reference_collections.referenceMapOf
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
        treeRoots = listOf(entryNode, secondNode, conditionalFalseNode, conditionalTrueNode),
        entryTreeRoot = entryNode,
        unconditionalLinks = referenceMapOf(entryNode to secondNode),
        conditionalTrueLinks = referenceMapOf(secondNode to conditionalTrueNode),
        conditionalFalseLinks = referenceMapOf(secondNode to conditionalFalseNode)
    )

    @Test
    fun `test pass entryTreeRoot in constructor`() {
        val cfg = ControlFlowGraphBuilder(entryNode).build()

        assertEquals(cfg.entryTreeRoot, entryNode)
        assertEquals(cfg.treeRoots, listOf(entryNode))
        assertEquals(cfg.unconditionalLinks, referenceMapOf())
        assertEquals(cfg.conditionalFalseLinks, referenceMapOf())
        assertEquals(cfg.conditionalTrueLinks, referenceMapOf())
    }

    @Test
    fun `test makeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.makeRoot(entryNode)
        assertEquals(ControlFlowGraphBuilder(entryNode).build(), cfgBuilder.build())
    }

    @Test
    fun `test makeRoot when already specified`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        assertFailsWith<IncorrectControlFlowGraphError> { cfgBuilder.makeRoot(entryNode) }
    }

    @Test
    fun `test addLink with null to entry link`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addLink(null, entryNode)
        cfgBuilder.addLink(Pair(entryNode, CFGLinkType.UNCONDITIONAL), secondNode)
        cfgBuilder.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_FALSE), conditionalFalseNode)
        cfgBuilder.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_TRUE), conditionalTrueNode)
        val cfg = cfgBuilder.build()
        assertEquals(expectedCFG, cfg)
    }

    @Test
    fun `test addAllFrom`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addAllFrom(expectedCFG)

        assertEquals(expectedCFG, cfgBuilder.build())
    }

    @Test
    fun `test addAllFrom without modifying entryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        cfgBuilder.addAllFrom(expectedCFG)

        val cfg = cfgBuilder.build()
        assertEquals(expectedCFG, cfg)
    }

    @Test
    fun `test addLinkFromAllFinalRoots when no entryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, entryNode)
        assertEquals(ControlFlowGraphBuilder(entryNode).build(), cfgBuilder.build())
    }

    @Test
    fun `test addLinkFromAllFinalRoots`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, secondNode)
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.CONDITIONAL_FALSE, conditionalFalseNode)
        cfgBuilder.addLink(Pair(secondNode, CFGLinkType.CONDITIONAL_TRUE), conditionalTrueNode)
        assertEquals(expectedCFG, cfgBuilder.build())
    }
}
