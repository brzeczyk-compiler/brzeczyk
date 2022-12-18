package compiler.intermediate_form

import kotlin.collections.HashMap

// The implementation below uses HashSets/HashMaps in various places to ensure that some operations run in O(1)

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
        private val allocationMap = HashMap<Register, Register>().apply { putAll(accessibleRegisters.associateWith { it }) }
        private val availableColorsMap = HashMap<Register, HashSet<Register>>()
        private val spilledRegisters = mutableListOf<Register>()
        fun Register.isColored() = allocationMap.contains(this)
        fun Register.color() = allocationMap[this]
        fun Register.availableColors() = availableColorsMap.getOrPut(this) { accessibleRegisters.toHashSet() }
        fun Register.assignColor(color: Register) {
            allocationMap[this] = color
            graph[this]!!.forEach { availableColorsMap.getOrPut(it) { accessibleRegisters.toHashSet() }.remove(color) }
        }

        fun Register.spill() = spilledRegisters.add(this)
        fun createAllocationResult() = AllocationResult(allocationMap, spilledRegisters)
    }

    fun allocateRegisters(
        livenessGraphs: Liveness.LivenessGraphs,
        accessibleRegisters: List<Register>,
    ): AllocationResult = GraphColoring(livenessGraphs.interferenceGraph, accessibleRegisters).run {
        val copyGraphWithoutInterferences = livenessGraphs.copyGraph.mapValues { it.value - livenessGraphs.interferenceGraph[it.key]!! }

        for (register in livenessGraphs.interferenceGraph.sortedByRemovingNodesWithSmallestDeg().reversed()) {

            val bestForColored: Register? by lazy(LazyThreadSafetyMode.NONE) {
                findBestFitForColoredCopyGraphNeighbours(register, copyGraphWithoutInterferences)
            }
            val bestForUncolored: Register? by lazy(LazyThreadSafetyMode.NONE) {
                findBestFitForUncoloredCopyGraphNeighbours(register, copyGraphWithoutInterferences)
            }

            when {
                register.isColored() -> {}

                register.availableColors().isEmpty() -> {
                    register.spill()
                }

                bestForColored != null -> {
                    register.assignColor(bestForColored!!)
                }

                bestForUncolored != null -> {
                    register.assignColor(bestForUncolored!!)
                }

                else -> {
                    register.assignColor(register.availableColors().first())
                }
            }
        }

        return createAllocationResult()
    }

    private fun GraphColoring.findBestFitForColoredCopyGraphNeighbours(
        register: Register,
        copyGraphWithoutInterferences: Map<Register, Set<Register>>
    ): Register? {
        val availableColorsOfCopyGraphNeighbours =
            copyGraphWithoutInterferences[register]!!.filter { it.color() in register.availableColors() }.map { it.color()!! }
        return availableColorsOfCopyGraphNeighbours.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    private fun GraphColoring.findBestFitForUncoloredCopyGraphNeighbours(
        register: Register,
        copyGraphWithoutInterferences: Map<Register, Set<Register>>
    ): Register? {
        val uncoloredCopyGraphNeighbours = copyGraphWithoutInterferences[register]!!.filter { !it.isColored() }
        val commonAvailableColors = uncoloredCopyGraphNeighbours.map { it.availableColors() intersect register.availableColors() }
        return commonAvailableColors.flatten().groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }
}
