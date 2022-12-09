package compiler.intermediate_form

import java.util.LinkedList
import kotlin.collections.HashMap

object Allocation {
    data class AllocationResult(
        val allocatedRegisters: Map<Register, Register>,
        val spilledRegisters: List<Register>,
    )

    private fun Map<Register, Set<Register>>.sortedByRemovingNodesWithSmallestDeg(): List<Register> = mutableListOf<Register>().apply {
        val graph = this@sortedByRemovingNodesWithSmallestDeg.mapValues { it.value.toHashSet() }.toMutableMap()

        fun removeFromGraph(node: Register) {
            graph[node]!!.forEach { graph[it]!!.remove(node) }
            graph.remove(node)
        }

        fun getNodeWithSmallestDegree(): Register = graph.minByOrNull { it.value.size }!!.key

        while (graph.isNotEmpty())
            getNodeWithSmallestDegree().let {
                removeFromGraph(it)
                add(it)
            }
    }

    private class GraphColoring(
        val graph: Map<Register, Set<Register>>,
        val accessibleRegisters: List<Register>
    ) {
        val allocationMap = HashMap<Register, Register>().apply { putAll(accessibleRegisters.associateWith { it }) }
        private val neighbourColorsMap = HashMap<Register, HashSet<Register>>()
        val spilledRegisters = LinkedList<Register>()
        fun Register.isColored() = allocationMap.contains(this)
        fun Register.color() = allocationMap[this]
        fun Register.neighbourColors(): Set<Register> = neighbourColorsMap.getOrPut(this) { HashSet() }
        fun Register.availableColors() = (accessibleRegisters - neighbourColors()).toSet()
        fun Register.assignColor(color: Register) {
            allocationMap[this] = color
            graph[this]!!.forEach { neighbourColorsMap.getOrPut(it) { HashSet() }.add(color) }
        }
    }

    fun allocateRegisters(
        linearProgram: List<Asmable>,
        livenessGraphs: Liveness.LivenessGraphs,
        accessibleRegisters: List<Register>,
    ): AllocationResult = GraphColoring(livenessGraphs.interferenceGraph, accessibleRegisters).run {
        val copyGraphWithoutInterferences = livenessGraphs.copyGraph.mapValues { it.value - livenessGraphs.interferenceGraph[it.key]!! }

        livenessGraphs.interferenceGraph.sortedByRemovingNodesWithSmallestDeg().reversed().forEach { register ->
            if (register.isColored()) return@forEach

            val availableColors = register.availableColors()
            if (availableColors.isEmpty()) {
                spilledRegisters.add(register)
                return@forEach
            }

            if (findBestFitForColoredCopyGraphNeighbours(register, availableColors, copyGraphWithoutInterferences)) {
                return@forEach
            }

            if (findBestFitForUncoloredCopyGraphNeighbours(register, availableColors, copyGraphWithoutInterferences)) {
                return@forEach
            }

            register.assignColor(availableColors.first())
        }

        return AllocationResult(allocationMap, spilledRegisters)
    }

    private fun GraphColoring.findBestFitForColoredCopyGraphNeighbours(
        register: Register,
        availableColors: Set<Register>,
        copyGraphWithoutInterferences: Map<Register, Set<Register>>
    ): Boolean {
        val colorsOfCopyGraphNeighbours =
            copyGraphWithoutInterferences[register]!!.filter { it.color() in availableColors }.map { it.color()!! }
        if (colorsOfCopyGraphNeighbours.isNotEmpty()) {
            val mostCommonColor = colorsOfCopyGraphNeighbours.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
            register.assignColor(mostCommonColor)
            return true
        }
        return false
    }

    private fun GraphColoring.findBestFitForUncoloredCopyGraphNeighbours(
        register: Register,
        availableColors: Set<Register>,
        copyGraphWithoutInterferences: Map<Register, Set<Register>>
    ): Boolean {
        val uncoloredCopyGraphNeighbours = copyGraphWithoutInterferences[register]!!.filter { !it.isColored() }
        val commonAvailableColors = uncoloredCopyGraphNeighbours.map { it.availableColors() intersect availableColors }
        commonAvailableColors.flatten().groupingBy { it }.eachCount().let {
            if (it.isEmpty()) return@let
            val mostCommonColor = it.maxByOrNull { it.value }!!.key
            register.assignColor(mostCommonColor)
            return true
        }
        return false
    }
}
