package compiler.intermediate_form

import compiler.common.Indexed
import java.util.LinkedList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

object Allocation {
    data class AllocationResult(
        val allocatedRegisters: Map<Register, Register>,
        val spilledRegisters: List<Register>,
    )

    private data class Node(val registers: Set<Register>)

    private interface GraphObserver {
        fun nodeRemoved(node: Node, edgesTo: Collection<Node>)
        fun edgesAdded(node: Node, edgesTo: Collection<Node>)
    }

    private class Graph {
        private val nodesSet: MutableSet<Node> = HashSet()
        private val edgesMap: MutableMap<Node, MutableSet<Node>> = HashMap()
        private val observers: MutableList<GraphObserver> = mutableListOf()

        val nodes: Set<Node>
            get() = nodesSet

        val edges: Map<Node, Set<Node>>
            get() = edgesMap

        val degreeOf
            get() = object : Indexed<Node, Int> {
                override fun get(a: Node): Int {
                    return edgesMap[a]!!.size
                }
            }

        fun removeNode(node: Node) {
            val removedEdgesTo = edgesMap[node]!!
            edgesMap.remove(node)
            nodesSet.remove(node)
            removedEdgesTo.forEach { edgesMap[it]!!.remove(node) }

            observers.forEach { it.nodeRemoved(node, removedEdgesTo) }
        }

        fun addEdges(node: Node, edgesTo: Collection<Node>) {
            nodesSet.add(node)
            edgesMap.getOrPut(node) { HashSet() }.addAll(edgesTo)
            edgesTo.forEach {
                nodesSet.add(it)
                edgesMap.getOrPut(it) { HashSet() }.add(node)
            }

            observers.forEach { it.edgesAdded(node, edgesTo) }
        }

        fun mergeNodes(node1: Node, node2: Node) {
            val nodeToAdd = Node(node1.registers union node2.registers)
            val edgesToAdd = edges[node1]!! union edges[node2]!!

            removeNode(node1)
            removeNode(node2)
            addEdges(nodeToAdd, edgesToAdd)
        }

        fun addObserver(observer: GraphObserver) = observers.add(observer)
    }

    // helper properties

    private val Map<Register, Set<Register>>.asGraph: Graph
        get() = Graph().also { graph ->
            forEach { entry ->
                graph.addEdges(
                    entry.key.asNode,
                    entry.value.map { it.asNode }
                )
            }
        }

    private val Register.asNode
        get() = Node(setOf(this))

    private val Graph.nodeWithSmallestDegree: Node?
        get() = this.nodes.minByOrNull { this.degreeOf[it] }

    fun allocateRegisters(
        linearProgram: List<Asmable>,
        livenessGraphs: Liveness.LivenessGraphs,
        accessibleRegisters: List<Register>,
    ): AllocationResult {
        val graph = livenessGraphs.interferenceGraph.asGraph
        val allocationMap = HashMap<Register, Register>().apply { putAll(accessibleRegisters.associateWith { it }) }
        val spilledRegisters = LinkedList<Register>()

        fun Node.neighbourColors() =
            this.registers.map { livenessGraphs.interferenceGraph[it]!! }.flatten().mapNotNull { allocationMap[it] }.toSet()

        val nodesOrder = LinkedList<Node>().apply {
            while (graph.nodes.isNotEmpty()) {
                graph.nodeWithSmallestDegree!!.let {
                    add(it)
                    graph.removeNode(it)
                }
            }
        }

        for (node in nodesOrder.reversed()) {
            val neigh = node.neighbourColors()
            val availableRegisters = accessibleRegisters - neigh
            if (availableRegisters.isNotEmpty()) {
                val chosenColor = availableRegisters.first()
                allocationMap.putAll(node.registers.associateWith { chosenColor })
            } else {
                spilledRegisters.addAll(node.registers)
            }
        }

        return AllocationResult(allocationMap, spilledRegisters)
    }
}
