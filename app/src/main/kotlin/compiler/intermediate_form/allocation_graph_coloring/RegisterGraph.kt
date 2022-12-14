package compiler.intermediate_form.allocation_graph_coloring

import compiler.intermediate_form.Liveness
import compiler.intermediate_form.Register
import java.util.LinkedList
import java.util.Stack
import kotlin.collections.HashMap
import kotlin.collections.HashSet

// RegisterGraph implements safe coalescing of registers in the interference graph
// Implementation is based on the algorithm described in the book "Modern Compiler Implementation in Java" by Andrew W. Appel, chapter 11: "Register Allocation"
// HashSets/HashMaps are used in various places to ensure that some operations run in O(1)

class RegisterGraph private constructor(
    livenessGraphs: Liveness.LivenessGraphs,
    val accessibleRegisters: HashSet<Register>
) {

    private val K = accessibleRegisters.size
    data class Node(val registers: HashSet<Register>, val containsHardwareRegister: Boolean = false)

    companion object {
        fun process( // main algorithm
            livenessGraphs: Liveness.LivenessGraphs,
            accessibleRegisters: List<Register>
        ): Stack<Node> {
            val stack = Stack<Node>()

            with(RegisterGraph(livenessGraphs, accessibleRegisters.toHashSet())) {
                while (isNotEmpty()) run {
                    simplify()?.let {
                        stack.push(it)
                        return@run
                    }
                    if (coalesce()) return@run
                    freeze()?.let {
                        stack.push(it)
                        return@run
                    }
                    stack.push(spill())
                }
            }

            return stack
        }
    }

    // --------------- graph representation ------------

    private val interferenceGraph: MutableMap<Node, HashSet<Node>> = livenessGraphs.interferenceGraph.asMutableNodeGraph()
    private val copyGraph: MutableMap<Node, HashSet<Node>> = livenessGraphs.copyGraph.asMutableNodeGraph().withDefault { hashSetOf() }

    // ---------------- invariants ---------------------

    private val simplifyCandidates: HashSet<Node> = // deg < K, no copy edges
        interferenceGraph.filter { it.value.size < K && copyGraph.getValue(it.key).size == 0 }.map { it.key }.toHashSet()
    private val freezeCandidates: HashSet<Node> = // def < K, some copy edges
        interferenceGraph.filter { it.value.size < K && it.key !in simplifyCandidates }.map { it.key }.toHashSet()
    private val spillCandidates: HashSet<Node> = // deg >=K
        (interferenceGraph.keys - simplifyCandidates - freezeCandidates).toHashSet()

    // ------------ candidates for coalesce -------------

    private val coalesceCandidates = LinkedList<Pair<Node, Node>>()

    init {
        enableMoves(copyGraph.keys.toHashSet()) // initially, we add all move edges to coalesceCandidates
    }

    // -------------- main functionalities --------------

    private fun isNotEmpty(): Boolean = interferenceGraph.isNotEmpty()

    private fun simplify(): Node? = simplifyCandidates.firstOrNull()?.also { remove(it) }

    private fun coalesce(): Boolean {

        // --- helper functions ---

        fun checkBasicMergeAbility(potentialSysReg: Node, nonSysReg: Node): Boolean {
            if (potentialSysReg !in interferenceGraph || nonSysReg !in interferenceGraph) return false // one of nodes was previously removed
            if (
                interferenceGraph[potentialSysReg]!!.contains(nonSysReg) || // interference between nodes
                nonSysReg.containsHardwareRegister // both nodes contain system registers
            ) {
                copyGraph.removeEdge(nonSysReg, potentialSysReg) // we won't be able to merge
                return false
            }
            return true
        }

        fun georgeCondition(sysReg: Node, toMerge: Node): Boolean =
            interferenceGraph[toMerge]!!.all {
                it.deg() < K || it.containsHardwareRegister || interferenceGraph[sysReg]!!.contains(it)
            }

        fun briggsCondition(first: Node, second: Node): Boolean {
            val commonOfSizeK: Int =
                (interferenceGraph[first]!! intersect interferenceGraph[second]!!).count { it.deg() == K }
            val allOfSizeGeK: Int =
                (interferenceGraph[first]!! + interferenceGraph[second]!!).count { it.deg() >= K }
            return allOfSizeGeK - commonOfSizeK < K
        }

        fun Pair<Node, Node>.potentialSystemRegisterFirst() =
            if (second.containsHardwareRegister) Pair(second, first)
            else this

        // --- main impl ---

        val candidate = coalesceCandidates.ifEmpty { return false }.pop().potentialSystemRegisterFirst()

        checkBasicMergeAbility(candidate.first, candidate.second).ifFalse { return false }

        val ableToCoalesce: Boolean = candidate.let { (potentialSysReg, secondReg) ->
            potentialSysReg.containsHardwareRegister && georgeCondition(potentialSysReg, secondReg) || // system registers may have large degree, so we check the George condition in O(secondReg.deg())
                !potentialSysReg.containsHardwareRegister && briggsCondition(potentialSysReg, secondReg)
        }

        if (ableToCoalesce) {
            candidate.let { (mergedTo, toRemove) ->
                mergedTo.registers.addAll(toRemove.registers)
                interferenceGraph[toRemove]!!.forEach { interferenceGraph.addEdge(it, mergedTo) }
                copyGraph.getValue(toRemove).forEach {
                    copyGraph.addEdge(it, mergedTo)
                    coalesceCandidates.add(Pair(it, mergedTo))
                }
                remove(toRemove)

                if (mergedTo.deg() >= K) { // mergedTo cannot be in simplifyCandidates, because those nodes were removed in the previous phase
                    freezeCandidates.remove(mergedTo)
                    spillCandidates.add(mergedTo)
                }
            }
        }

        return ableToCoalesce
    }

    private fun freeze(): Node? = freezeCandidates.minByOrNull { it.deg() }?.also { remove(it) }

    private fun spill(): Node = spillCandidates.minByOrNull { it.deg() }!!.also { remove(it) }

    // -------------- helper functions -------------------

    private fun remove(node: Node) {
        val neighbours = interferenceGraph[node]!!
        interferenceGraph.removeNode(node)
        copyGraph.removeNode(node)

        neighbours.filter { it.deg() == K - 1 }.forEach {
            spillCandidates.remove(it)
            if (copyGraph[it]!!.size == 0) {
                simplifyCandidates.add(it)
            } else freezeCandidates.add(it)
            enableMoves((interferenceGraph[it]!! + setOf(it)).toHashSet())
        }
        removeFromAllSets(node)
    }

    private fun enableMoves(nodes: HashSet<Node>) {
        coalesceCandidates.addAll(
            copyGraph.filter { it.key in nodes }.flatMap { (reg, set) -> set.map { Pair(reg, it) } }
        )
    }

    // --------------- simple helper functions --------------------

    private fun Node.deg() = interferenceGraph[this]!!.size
    private fun Register.toNode() = Node(hashSetOf(this), this in accessibleRegisters)

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

    private fun removeFromAllSets(node: Node) =
        simplifyCandidates.remove(node) || freezeCandidates.remove(node) || spillCandidates.remove(node)

    private inline fun Boolean.ifFalse(action: () -> Unit) {
        if (!this) action()
    }
}
