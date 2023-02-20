package compiler.intermediate

import compiler.utils.Ref
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgramControlFlowPlannerTest {
    private val controlFlowPlanner = ControlFlowPlanner(mockk())

    @Test
    fun `test attachPrologueAndEpilogue for empty function`() {
        val prologue = IFTNode.MemoryLabel("prologue")
        val bodyCFG = ControlFlowGraphBuilder().build()
        val epilogue = IFTNode.MemoryLabel("prologue")

        val result = controlFlowPlanner.attachPrologueAndEpilogue(
            bodyCFG,
            ControlFlowGraphBuilder(prologue).build(),
            ControlFlowGraphBuilder(epilogue).build(),
        )

        val expected = ControlFlowGraphBuilder(prologue)
        expected.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            epilogue
        )

        assertEquals(expected.build(), result)
    }

    @Test
    fun `test attachPrologueAndEpilogue function with linear control flow`() {
        val prologue = IFTNode.MemoryLabel("prologue")
        val middleNode = IFTNode.Const(123)
        val epilogue = IFTNode.MemoryLabel("prologue")

        val bodyCFG = ControlFlowGraphBuilder(middleNode).build()

        val result = controlFlowPlanner.attachPrologueAndEpilogue(
            bodyCFG,
            ControlFlowGraphBuilder(prologue).build(),
            ControlFlowGraphBuilder(epilogue).build(),
        )

        val expected = ControlFlowGraphBuilder(prologue)
        expected.addLink(Pair(Ref(prologue), CFGLinkType.UNCONDITIONAL), Ref(middleNode))
        expected.addLink(Pair(Ref(middleNode), CFGLinkType.UNCONDITIONAL), Ref(epilogue))

        assertEquals(expected.build(), result)
    }

    @Test
    fun `test attachPrologueAndEpilogue for branching function`() {
        val prologue = IFTNode.MemoryLabel("prologue")
        val epilogue = IFTNode.MemoryLabel("prologue")

        val node1 = IFTNode.MemoryLabel("node1")
        val node2 = IFTNode.MemoryLabel("node2")
        val node3 = IFTNode.MemoryLabel("node3")
        val node4T = IFTNode.MemoryLabel("node4T")
        val node4F = IFTNode.MemoryLabel("node4F")

        val bodyCFGBuilder = ControlFlowGraphBuilder(node1)
        bodyCFGBuilder.addLink(Pair(Ref(node1), CFGLinkType.CONDITIONAL_TRUE), Ref(node2))
        bodyCFGBuilder.addLink(Pair(Ref(node2), CFGLinkType.CONDITIONAL_FALSE), Ref(node3))
        bodyCFGBuilder.addLink(Pair(Ref(node3), CFGLinkType.CONDITIONAL_FALSE), Ref(node4F))
        bodyCFGBuilder.addLink(Pair(Ref(node3), CFGLinkType.CONDITIONAL_TRUE), Ref(node4T))

        val result = controlFlowPlanner.attachPrologueAndEpilogue(
            bodyCFGBuilder.build(),
            ControlFlowGraphBuilder(prologue).build(),
            ControlFlowGraphBuilder(epilogue).build(),
        )

        val expected = ControlFlowGraphBuilder(prologue)
        expected.addLink(Pair(Ref(prologue), CFGLinkType.UNCONDITIONAL), Ref(node1))
        expected.addAllFrom(bodyCFGBuilder.build())

        // fill missing conditional links
        expected.addLink(Pair(Ref(node1), CFGLinkType.CONDITIONAL_FALSE), Ref(epilogue))
        expected.addLink(Pair(Ref(node2), CFGLinkType.CONDITIONAL_TRUE), Ref(epilogue))
        // link from both return branches to epilogue
        expected.addLink(Pair(Ref(node4T), CFGLinkType.UNCONDITIONAL), Ref(epilogue))
        expected.addLink(Pair(Ref(node4F), CFGLinkType.UNCONDITIONAL), Ref(epilogue))

        assertEquals(expected.build(), result)
    }
}
