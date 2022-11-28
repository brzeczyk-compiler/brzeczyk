package compiler.intermediate_form
import compiler.common.reference_collections.referenceMapOf
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `test pass entryTreeRoot in constructor`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)

        val cfgBuilder2 = ControlFlowGraphBuilder()
        cfgBuilder2.addLink(null, entryNode)

        assertEquals(cfgBuilder.build(), cfgBuilder2.build())
    }

    @Test
    fun `test addAllFrom`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addAllFrom(expectedCFG, true)

        assertEquals(expectedCFG, cfgBuilder.build())
    }

    @Test
    fun `test addAllFrom without modifying entryTreeRoot`() {
        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addAllFrom(expectedCFG, false)

        val cfg = cfgBuilder.build()
        assertEquals(null, cfg.entryTreeRoot)
    }

    @Test
    fun `test updateFinalLinks`() {
        val cfgBuilder = ControlFlowGraphBuilder(entryNode)
        cfgBuilder.addLink(Pair(entryNode, CFGLinkType.UNCONDITIONAL), secondNode)
        cfgBuilder.updateFinalLinks {
            listOf(
                Triple(it.first, CFGLinkType.CONDITIONAL_TRUE, conditionalTrueNode),
                Triple(it.first, CFGLinkType.CONDITIONAL_FALSE, conditionalFalseNode),
            )
        }
    }
}
