package compiler.intermediate_form

import compiler.common.intermediate_form.Covering
import compiler.common.reference_collections.referenceHashMapOf
import compiler.intermediate_form.Instruction.UnconditionalJumpInstruction.Jmp
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class LinearizationTest {
    private val unconditional = referenceHashMapOf<Instruction, IFTNode>()
    private val conditional = referenceHashMapOf<Instruction, Triple<IFTNode, Boolean, String>>()

    private val covering = object : Covering {
        override fun coverUnconditional(iftNode: IntermediateFormTreeNode): List<Instruction> {
            val instruction = Instruction.NoOp()
            unconditional[instruction] = iftNode
            return listOf(instruction)
        }

        override fun coverConditional(iftNode: IntermediateFormTreeNode, targetLabel: String, invert: Boolean): List<Instruction> {
            val instruction = Instruction.NoOp()
            conditional[instruction] = Triple(iftNode, !invert, targetLabel)
            return listOf(instruction)
        }
    }

    private sealed class AsmablePattern

    private class Uncoditional(val node: IFTNode) : AsmablePattern()
    private class Conditional(val node: IFTNode, val whenTrue: Boolean, val targetLabelNumber: Int) : AsmablePattern()
    private object NextLabel : AsmablePattern()
    private class Jump(val targetLabelNumber: Int) : AsmablePattern()

    private fun assertLinearizationMatches(expected: List<AsmablePattern>, actual: List<Asmable>) {
        assertEquals(expected.size, actual.size)

        val labels = mutableMapOf<String, Int>()
        var labelCount = 0

        for (label in actual.filterIsInstance<Label>().map { it.label }) {
            if (label !in labels)
                labels[label] = labels.size
        }

        for ((expectedItem, actualItem) in expected.zip(actual)) {
            when (expectedItem) {
                is Uncoditional -> {
                    assertIs<Instruction>(actualItem)
                    assertContains(unconditional, actualItem)
                    assertSame(expectedItem.node, unconditional[actualItem]!!)
                }

                is Conditional -> {
                    assertIs<Instruction>(actualItem)
                    assertContains(conditional, actualItem)
                    assertSame(expectedItem.node, conditional[actualItem]!!.first)
                    assertEquals(expectedItem.whenTrue, conditional[actualItem]!!.second)
                    assertEquals(expectedItem.targetLabelNumber, labels[conditional[actualItem]!!.third])
                }

                is NextLabel -> {
                    assertIs<Label>(actualItem)
                    assertEquals(labelCount++, labels[actualItem.label])
                }

                is Jump -> {
                    assertIs<Jmp>(actualItem)
                    assertEquals(expectedItem.targetLabelNumber, labels[actualItem.targetLabel])
                }
            }
        }
    }

    private fun newNode() = IntermediateFormTreeNode.NoOp()

    @Test
    fun `empty graph`() {
        val cfg = ControlFlowGraph(emptyList(), null, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf())

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(emptyList(), result)
    }

    @Test
    fun `single node`() {
        val node = newNode()
        val cfg = ControlFlowGraph(listOf(node), node, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf())

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(listOf(Uncoditional(node)), result)
    }

    @Test
    fun `two nodes`() {
        val node1 = newNode()
        val node2 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2),
            node1,
            referenceHashMapOf(node1 to node2),
            referenceHashMapOf(),
            referenceHashMapOf()
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                Uncoditional(node1),
                Uncoditional(node2)
            ),
            result
        )
    }

    @Test
    fun `infinite loop`() {
        val node = newNode()

        val cfg = ControlFlowGraph(
            listOf(node),
            node,
            referenceHashMapOf(node to node),
            referenceHashMapOf(),
            referenceHashMapOf()
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Uncoditional(node),
                Jump(0)
            ),
            result
        )
    }

    @Test
    fun `one-branch conditional`() {
        val node1 = newNode()
        val node2 = newNode()
        val node3 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3),
            node1,
            referenceHashMapOf(node2 to node3),
            referenceHashMapOf(node1 to node2),
            referenceHashMapOf(node1 to node3)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                Conditional(node1, false, 0),
                Uncoditional(node2),
                NextLabel,
                Uncoditional(node3)
            ),
            result
        )
    }

    @Test
    fun `two-branch conditional`() {
        val node1 = newNode()
        val node2 = newNode()
        val node3 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3),
            node1,
            referenceHashMapOf(),
            referenceHashMapOf(node1 to node2),
            referenceHashMapOf(node1 to node3)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                Conditional(node1, false, 0),
                Uncoditional(node2),
                Jump(1),
                NextLabel,
                Uncoditional(node3),
                NextLabel
            ),
            result
        )
    }

    @Test
    fun `empty loop checking if true`() {
        val node1 = newNode()
        val node2 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2),
            node1,
            referenceHashMapOf(),
            referenceHashMapOf(node1 to node1),
            referenceHashMapOf(node1 to node2)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node1, true, 0),
                Uncoditional(node2)
            ),
            result
        )
    }

    @Test
    fun `empty loop checking if false`() {
        val node1 = newNode()
        val node2 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2),
            node1,
            referenceHashMapOf(),
            referenceHashMapOf(node1 to node2),
            referenceHashMapOf(node1 to node1)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node1, false, 0),
                Uncoditional(node2)
            ),
            result
        )
    }

    @Test
    fun `loop checking if true`() {
        val node1 = newNode()
        val node2 = newNode()
        val node3 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3),
            node1,
            referenceHashMapOf(node2 to node1),
            referenceHashMapOf(node1 to node2),
            referenceHashMapOf(node1 to node3)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node1, false, 1),
                Uncoditional(node2),
                Jump(0),
                NextLabel,
                Uncoditional(node3)
            ),
            result
        )
    }

    @Test
    fun `loop checking if false`() {
        val node1 = newNode()
        val node2 = newNode()
        val node3 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3),
            node1,
            referenceHashMapOf(node2 to node1),
            referenceHashMapOf(node1 to node3),
            referenceHashMapOf(node1 to node2)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node1, false, 1),
                Uncoditional(node3),
                Jump(2),
                NextLabel,
                Uncoditional(node2),
                Jump(0),
                NextLabel
            ),
            result
        )
    }

    @Test
    fun `nested empty loop`() {
        val node1 = newNode()
        val node2 = newNode()
        val node3 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3),
            node1,
            referenceHashMapOf(),
            referenceHashMapOf(node1 to node2, node2 to node2),
            referenceHashMapOf(node1 to node3, node2 to node1)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node1, false, 2),
                NextLabel,
                Conditional(node2, true, 1),
                Jump(0),
                NextLabel,
                Uncoditional(node3)
            ),
            result
        )
    }

    @Test
    fun `end if true`() {
        val node1 = newNode()
        val node2 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2),
            node1,
            referenceHashMapOf(),
            referenceHashMapOf(),
            referenceHashMapOf(node1 to node2)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                Conditional(node1, true, 0),
                Uncoditional(node2),
                NextLabel
            ),
            result
        )
    }

    @Test
    fun `end if false`() {
        val node1 = newNode()
        val node2 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2),
            node1,
            referenceHashMapOf(),
            referenceHashMapOf(node1 to node2),
            referenceHashMapOf()
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                Conditional(node1, false, 0),
                Uncoditional(node2),
                NextLabel
            ),
            result
        )
    }

    @Test
    fun `end after empty loop checking if true`() {
        val node = newNode()

        val cfg = ControlFlowGraph(
            listOf(node),
            node,
            referenceHashMapOf(),
            referenceHashMapOf(node to node),
            referenceHashMapOf()
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node, true, 0)
            ),
            result
        )
    }

    @Test
    fun `end after empty loop checking if false`() {
        val node = newNode()

        val cfg = ControlFlowGraph(
            listOf(node),
            node,
            referenceHashMapOf(),
            referenceHashMapOf(),
            referenceHashMapOf(node to node)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node, false, 0)
            ),
            result
        )
    }
}
