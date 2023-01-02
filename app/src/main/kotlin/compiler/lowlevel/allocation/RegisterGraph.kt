package compiler.lowlevel.allocation

import compiler.intermediate.Register
import compiler.lowlevel.dataflow.Liveness
import java.util.LinkedList
import java.util.Stack
import kotlin.collections.HashMap
import kotlin.collections.HashSet

// RegisterGraph implements safe coalescing of registers in the interference graph
// Implementation is based on the algorithm described in the book "Modern Compiler Implementation in Java" by Andrew W. Appel, chapter 11: "Register Allocation"
// HashSets/HashMaps are used in various places to ensure that some operations run in O(1)

class RegisterGraph private constructor(
    livenessGraphs: Liveness.LivenessGraphs,
    val selfColoredRegisters: HashSet<Register>,
    availableColors: HashSet<Register>
) {

    private val K = availableColors.size
    private val forbiddenColors = selfColoredRegisters - availableColors // colors that forbid merging

    // ------------ graph nodes -----------------

    private data class NodeData(val registers: HashSet<Register>, val containsSelfColoredRegister: Boolean)

    private val regToNodeData = HashMap<Register, NodeData>()

    inner class Node(private val founder: Register) {

        init {
            regToNodeData.putIfAbsent(founder, NodeData(hashSetOf(founder), founder in selfColoredRegisters))
        }

        val registers: HashSet<Register> by regToNodeData[founder]!!::registers
        val containsSelfColoredRegister: Boolean by regToNodeData[founder]!!::containsSelfColoredRegister

        override fun equals(other: Any?): Boolean {
            if (other !is Node) return false
            return founder === other.founder
        }

        override fun hashCode(): Int = founder.hashCode()
    }

    // -------------- main algorithm ------------------

    companion object {
        fun process(
            livenessGraphs: Liveness.LivenessGraphs,
            selfColoredRegisters: List<Register>,
            availableColors: List<Register>
        ): Stack<Node> {
            val stack = Stack<Node>()

            with(RegisterGraph(livenessGraphs, selfColoredRegisters.toHashSet(), availableColors.toHashSet())) {
                while (isNotEmpty()) when {
                    simplifyCandidates.isNotEmpty() -> stack.push(simplify()!!)
                    coalesceCandidates.isNotEmpty() -> coalesce()
                    freezeCandidates.isNotEmpty() -> stack.push(freeze()!!)
                    else -> stack.push(spill())
                }
            }
            return stack
        }
    }

    // --------------- graph representation ------------

    private val interferenceGraph: MutableMap<Node, HashSet<Node>> = livenessGraphs.interferenceGraph.asMutableNodeGraph()
    private val copyGraph: MutableMap<Node, HashSet<Node>> = livenessGraphs.copyGraph.asMutableNodeGraph().withDefault { hashSetOf() }

    // ---------------- invariants ---------------------

    private val simplifyCandidates: HashSet<Node> = hashSetOf()
    private val freezeCandidates: HashSet<Node> = hashSetOf()
    private val spillCandidates: HashSet<Node> = hashSetOf()

    private fun classifyNode(node: Node) =
        if (node.deg() >= K) spillCandidates // deg >= K
        else if (copyGraph.getValue(node).isEmpty()) simplifyCandidates // deg < K, no copy edges
        else freezeCandidates // deg < K, some copy edges

    init {
        interferenceGraph.keys.forEach { classifyNode(it).add(it) }
    }

    // ------------ candidates for coalesce -------------

    private val coalesceCandidates = LinkedList<Pair<Node, Node>>()

    init {
        enableMoves(copyGraph.keys.toHashSet()) // initially, we add all move edges to coalesceCandidates
    }

    // -------------- main functionalities --------------

    private fun isNotEmpty(): Boolean = interferenceGraph.isNotEmpty()

    private fun simplify(): Node? = simplifyCandidates.firstOrNull()?.also { remove(it) }

    private fun coalesce() {

        // --- helper functions ---

        fun checkBasicMergeAbility(potentiallySelfColReg: Node, nonSelfColReg: Node): Boolean {
            if (potentiallySelfColReg !in interferenceGraph || nonSelfColReg !in interferenceGraph) return false // one of nodes was previously removed
            if (
                interferenceGraph[potentiallySelfColReg]!!.contains(nonSelfColReg) || // interference between nodes
                nonSelfColReg.containsSelfColoredRegister || // both nodes contain self colored registers
                (potentiallySelfColReg.registers intersect forbiddenColors).isNotEmpty() // merging would give some register a forbidden color
            ) {
                copyGraph.removeEdge(nonSelfColReg, potentiallySelfColReg) // we won't be able to merge
                return false
            }
            return true
        }

        fun georgeCondition(sysReg: Node, toMerge: Node): Boolean =
            interferenceGraph[toMerge]!!.all {
                it.deg() < K || it.containsSelfColoredRegister || interferenceGraph[sysReg]!!.contains(it)
            }

        fun briggsCondition(first: Node, second: Node): Boolean {
            val commonOfSizeK: Int =
                (interferenceGraph[first]!! intersect interferenceGraph[second]!!).count { it.deg() == K }
            val allOfSizeGeK: Int =
                (interferenceGraph[first]!! + interferenceGraph[second]!!).count { it.deg() >= K }
            return allOfSizeGeK - commonOfSizeK < K
        }

        fun Pair<Node, Node>.potentiallySelfColoredRegisterFirst() =
            if (second.containsSelfColoredRegister) Pair(second, first)
            else this

        // --- main impl ---

        val candidate = coalesceCandidates.ifEmpty { return }.pop().potentiallySelfColoredRegisterFirst()

        checkBasicMergeAbility(candidate.first, candidate.second).ifFalse { return }

        val ableToCoalesce: Boolean = candidate.let { (potentialSelfColReg, secondReg) ->
            potentialSelfColReg.containsSelfColoredRegister && georgeCondition(potentialSelfColReg, secondReg) || // self colored registers may have large degree, so we check the George condition in O(secondReg.deg())
                !potentialSelfColReg.containsSelfColoredRegister && briggsCondition(potentialSelfColReg, secondReg)
        }

        if (ableToCoalesce) {
            candidate.let { (mergedTo, toRemove) ->
                copyGraph.removeEdge(mergedTo, toRemove)
                mergedTo.registers.addAll(toRemove.registers)
                interferenceGraph[toRemove]!!.forEach { interferenceGraph.addEdge(it, mergedTo) }
                copyGraph.getValue(toRemove).forEach {
                    copyGraph.addEdge(it, mergedTo)
                    coalesceCandidates.add(Pair(it, mergedTo))
                }
                remove(toRemove)
                reclassifyNode(mergedTo)
            }
        }
    }

    private fun freeze(): Node? = freezeCandidates.minByOrNull { it.deg() }?.also { remove(it) }

    private fun spill(): Node = spillCandidates.minByOrNull { it.deg() }!!.also { remove(it) }

    // -------------- helper functions -------------------

    private fun remove(node: Node) {
        val neighbours = interferenceGraph[node]!!
        interferenceGraph.removeNode(node)
        copyGraph.removeNode(node)
        removeFromSet(node)

        neighbours.forEach { reclassifyNode(it) }
        neighbours.filter { it.deg() == K - 1 }.forEach { enableMoves((interferenceGraph[it]!! + setOf(it)).toHashSet()) }
    }

    private fun enableMoves(nodes: HashSet<Node>) {
        coalesceCandidates.addAll(
            copyGraph.filter { it.key in nodes }.flatMap { (reg, set) -> set.map { Pair(reg, it) } }
        )
    }

    // --------------- simple helper functions --------------------

    private fun Node.deg() = interferenceGraph[this]!!.size
    private fun Register.toNode() = Node(this)

    private fun Map<Register, Set<Register>>.asMutableNodeGraph() =
        this.mapValues { it.value.map { it.toNode() }.toHashSet() }.mapKeys { it.key.toNode() }.run { HashMap(this) }

    private fun MutableMap<Node, HashSet<Node>>.removeNode(node: Node) {
        getValue(node).forEach { getValue(it).remove(node) }
        remove(node)
    }

    private fun MutableMap<Node, HashSet<Node>>.removeEdge(node1: Node, node2: Node) {
        getValue(node1).remove(node2)
        getValue(node2).remove(node1)
    }

    private fun MutableMap<Node, HashSet<Node>>.addEdge(node1: Node, node2: Node) {
        getOrPut(node1) { getValue(node1) }.add(node2)
        getOrPut(node2) { getValue(node2) }.add(node1)
    }

    private fun removeFromSet(node: Node) =
        simplifyCandidates.remove(node) || freezeCandidates.remove(node) || spillCandidates.remove(node)

    private fun reclassifyNode(node: Node) {
        removeFromSet(node)
        classifyNode(node).add(node)
    }

    private inline fun Boolean.ifFalse(action: () -> Unit) {
        if (!this) action()
    }
}
