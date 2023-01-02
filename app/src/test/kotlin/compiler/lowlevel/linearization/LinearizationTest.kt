package compiler.lowlevel.linearization

import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.IFTNode
import compiler.lowlevel.Asmable
import compiler.lowlevel.Instruction
import compiler.lowlevel.Instruction.UnconditionalJumpInstruction.Jmp
import compiler.lowlevel.Label
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf
import compiler.utils.mutableRefMapOf
import compiler.utils.refMapOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LinearizationTest {
    private val unconditional = mutableRefMapOf<Instruction, IFTNode>()
    private val conditional = mutableKeyRefMapOf<Instruction, Triple<Ref<IFTNode>, Boolean, String>>()

    private val covering = object : Covering {
        override fun coverUnconditional(iftNode: IFTNode): List<Instruction> {
            val instruction = Instruction.InPlaceInstruction.Dummy()
            unconditional[Ref(instruction)] = Ref(iftNode)
            return listOf(instruction)
        }

        override fun coverConditional(iftNode: IFTNode, targetLabel: String, invert: Boolean): List<Instruction> {
            val instruction = Instruction.InPlaceInstruction.Dummy()
            conditional[Ref(instruction)] = Triple(Ref(iftNode), !invert, targetLabel)
            return listOf(instruction)
        }
    }

    private sealed class AsmablePattern

    private class Unconditional(val node: IFTNode) : AsmablePattern()
    private class Conditional(val node: IFTNode, val whenTrue: Boolean, val targetLabelNumber: Int) : AsmablePattern()
    private object NextLabel : AsmablePattern()
    private class Jump(val targetLabelNumber: Int) : AsmablePattern()

    private fun assertLinearizationMatches(expected: List<AsmablePattern>, actual: List<Asmable>) {
        assertEquals(expected.size, actual.size)

        val labels = mutableListOf<String>()
        val labelNumbers = mutableMapOf<String, Int>()
        var labelCount = 0

        for (label in actual.filterIsInstance<Label>().map { it.label }) {
            if (label !in labelNumbers) {
                labels.add(label)
                labelNumbers[label] = labelNumbers.size
            }
        }

        for ((expectedItem, actualItem) in expected.zip(actual)) {
            when (expectedItem) {
                is Unconditional -> {
                    assertIs<Instruction>(actualItem)
                    assertContains(unconditional, Ref(actualItem))
                    assertEquals(Ref(expectedItem.node), unconditional[Ref(actualItem)]!!)
                }

                is Conditional -> {
                    assertIs<Instruction>(actualItem)
                    assertContains(conditional, Ref(actualItem))
                    assertEquals(Triple(Ref(expectedItem.node), expectedItem.whenTrue, labels[expectedItem.targetLabelNumber]), conditional[Ref(actualItem)]!!)
                }

                is NextLabel -> {
                    assertIs<Label>(actualItem)
                    assertEquals(labelCount++, labelNumbers[actualItem.label])
                }

                is Jump -> {
                    assertIs<Jmp>(actualItem)
                    assertEquals(expectedItem.targetLabelNumber, labelNumbers[actualItem.targetLabel])
                }
            }
        }
    }

    private fun newNode() = IFTNode.Dummy()

    @Test
    fun `empty graph`() {
        val cfg = ControlFlowGraph(listOf(), null, refMapOf(), refMapOf(), refMapOf())

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(emptyList(), result)
    }

    @Test
    fun `single node`() {
        val node = newNode()
        val cfg = ControlFlowGraph(listOf(node), node, refMapOf(), refMapOf(), refMapOf())

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(listOf(Unconditional(node)), result)
    }

    @Test
    fun `two nodes`() {
        val node1 = newNode()
        val node2 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2),
            node1,
            refMapOf(node1 to node2),
            refMapOf(),
            refMapOf()
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                Unconditional(node1),
                Unconditional(node2)
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
            refMapOf(node to node),
            refMapOf(),
            refMapOf()
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Unconditional(node),
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
            refMapOf(node2 to node3),
            refMapOf(node1 to node2),
            refMapOf(node1 to node3)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                Conditional(node1, false, 0),
                Unconditional(node2),
                NextLabel,
                Unconditional(node3)
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
            refMapOf(),
            refMapOf(node1 to node2),
            refMapOf(node1 to node3)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                Conditional(node1, false, 0),
                Unconditional(node2),
                NextLabel,
                Unconditional(node3),
            ),
            result
        )
    }

    @Test
    fun `nested conditional`() {
        val node1 = newNode()
        val node2 = newNode()
        val node3 = newNode()
        val node4 = newNode()
        val node5 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3, node4, node5),
            node1,
            refMapOf(),
            refMapOf(node1 to node2, node3 to node4),
            refMapOf(node1 to node3, node3 to node5)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                Conditional(node1, false, 0),
                Unconditional(node2),
                NextLabel,
                Conditional(node3, false, 1),
                Unconditional(node4),
                NextLabel,
                Unconditional(node5),
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
            refMapOf(),
            refMapOf(node1 to node1),
            refMapOf(node1 to node2)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node1, true, 0),
                Unconditional(node2)
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
            refMapOf(),
            refMapOf(node1 to node2),
            refMapOf(node1 to node1)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node1, false, 0),
                Unconditional(node2)
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
            refMapOf(node2 to node1),
            refMapOf(node1 to node2),
            refMapOf(node1 to node3)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node1, false, 1),
                Unconditional(node2),
                Jump(0),
                NextLabel,
                Unconditional(node3)
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
            refMapOf(node2 to node1),
            refMapOf(node1 to node3),
            refMapOf(node1 to node2)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node1, false, 1),
                Unconditional(node3),
                NextLabel,
                Unconditional(node2),
                Jump(0),
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
            refMapOf(),
            refMapOf(node1 to node2, node2 to node2),
            refMapOf(node1 to node3, node2 to node1)
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
                Unconditional(node3)
            ),
            result
        )
    }

    @Test
    fun `nested loop`() {
        val node1 = newNode()
        val node2 = newNode()
        val node3 = newNode()
        val node4 = newNode()

        val cfg = ControlFlowGraph(
            listOf(node1, node2, node3, node4),
            node1,
            refMapOf(node3 to node2),
            refMapOf(node1 to node2, node2 to node3),
            refMapOf(node1 to node4, node2 to node1)
        )

        val result = Linearization.linearize(cfg, covering)

        assertLinearizationMatches(
            listOf(
                NextLabel,
                Conditional(node1, false, 2),
                NextLabel,
                Conditional(node2, false, 0),
                Unconditional(node3),
                Jump(1),
                NextLabel,
                Unconditional(node4)
            ),
            result
        )
    }
}
