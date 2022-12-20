package compiler.intermediate_form.allocation_graph_coloring

import compiler.common.intermediate_form.Allocation
import compiler.intermediate_form.Liveness
import compiler.intermediate_form.Register

// GraphColoring colors registers and manages spills
// It uses some heuristics to decrease number of copies
// HashSets/HashMaps are used in various places to ensure that some operations run in O(1)

class GraphColoring private constructor(
    private val graph: Map<RegisterGraph.Node, Set<RegisterGraph.Node>>,
    private val accessibleRegisters: HashSet<Register>
) {

    companion object {

        fun color( // main algorithm
            livenessGraphs: Liveness.LivenessGraphs,
            accessibleRegisters: List<Register>
        ): Allocation.AllocationResult {
            val stack = RegisterGraph.process(livenessGraphs, accessibleRegisters)
            val (coalescedInterferenceGraph, coalescedCopyGraph) = livenessGraphs.toCoalesced(stack)

            with(GraphColoring(coalescedInterferenceGraph, accessibleRegisters.toHashSet())) {
                for (register in stack.reversed()) {

                    val bestForColored: Register? by lazy(LazyThreadSafetyMode.NONE) {
                        findBestFitForColoredCopyGraphNeighbours(register, coalescedCopyGraph)
                    }
                    val bestForUncolored: Register? by lazy(LazyThreadSafetyMode.NONE) {
                        findBestFitForUncoloredCopyGraphNeighbours(register, coalescedCopyGraph)
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
        }

        // ----------- helper function ----------------

        private fun Liveness.LivenessGraphs.toCoalesced(nodes: Collection<RegisterGraph.Node>): Pair<Map<RegisterGraph.Node, Set<RegisterGraph.Node>>, Map<RegisterGraph.Node, Set<RegisterGraph.Node>>> =
            Pair(HashMap<RegisterGraph.Node, HashSet<RegisterGraph.Node>>(), HashMap<RegisterGraph.Node, HashSet<RegisterGraph.Node>>()).also { (interferenceNodeGraph, copyNodeGraph) ->
                val regToNode = nodes.flatMap { node -> node.registers.associateWith { node }.toList() }.toMap().let { HashMap(it) }

                fun Register.toNode() = regToNode[this]!!
                fun HashMap<RegisterGraph.Node, HashSet<RegisterGraph.Node>>.addEdge(left: RegisterGraph.Node, right: RegisterGraph.Node) {
                    putIfAbsent(left, hashSetOf())
                    putIfAbsent(right, hashSetOf())
                    this[left]!!.add(right)
                }

                fun build(initialMap: Map<Register, Set<Register>>, targetMap: HashMap<RegisterGraph.Node, HashSet<RegisterGraph.Node>>) {
                    initialMap.flatMap { (reg, neigh) -> neigh.map { reg to it }.toList() }
                        .map { it.first.toNode() to it.second.toNode() }
                        .forEach { targetMap.addEdge(it.first, it.second) }
                    regToNode.values.forEach { targetMap.putIfAbsent(it, hashSetOf()) }
                }

                build(interferenceGraph, interferenceNodeGraph)
                build(copyGraph, copyNodeGraph)

                copyNodeGraph.forEach { (node, set) ->
                    set.removeAll(interferenceNodeGraph[node]!!)
                }
                nodes.forEach {
                    interferenceNodeGraph.putIfAbsent(it, hashSetOf())
                }
            }.let { Pair(it.first, it.second.withDefault { hashSetOf() }) }
    }

    // ------------- coloring data --------------------

    private val allocationMap = HashMap<RegisterGraph.Node, Register>()
    private val availableColorsMap = HashMap<RegisterGraph.Node, HashSet<Register>>()
    private val spilledRegisters = mutableListOf<RegisterGraph.Node>()

    init {
        graph.keys.forEach { node ->
            (node.registers intersect accessibleRegisters).firstOrNull()?.let { node.assignColor(it) }
        }
    }

    // ------------ main functionalities -------------

    private fun RegisterGraph.Node.isColored() = allocationMap.contains(this)
    private fun RegisterGraph.Node.color() = allocationMap[this]
    private fun RegisterGraph.Node.availableColors() = availableColorsMap.getOrPut(this) { accessibleRegisters.toHashSet() }
    private fun RegisterGraph.Node.assignColor(color: Register) {
        allocationMap[this] = color
        graph[this]!!.forEach { availableColorsMap.getOrPut(it) { accessibleRegisters.toHashSet() }.remove(color) }
    }

    private fun RegisterGraph.Node.spill() = spilledRegisters.add(this)

    // ------------ coloring heuristics -------------

    private fun findBestFitForColoredCopyGraphNeighbours(
        register: RegisterGraph.Node,
        copyGraphWithoutInterferences: Map<RegisterGraph.Node, Set<RegisterGraph.Node>>
    ): Register? {
        val availableColorsOfCopyGraphNeighbours =
            copyGraphWithoutInterferences.getValue(register).filter { it.color() in register.availableColors() }.map { it.color()!! }
        return availableColorsOfCopyGraphNeighbours.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    private fun findBestFitForUncoloredCopyGraphNeighbours(
        register: RegisterGraph.Node,
        copyGraphWithoutInterferences: Map<RegisterGraph.Node, Set<RegisterGraph.Node>>
    ): Register? {
        val uncoloredCopyGraphNeighbours = copyGraphWithoutInterferences.getValue(register).filter { !it.isColored() }
        val commonAvailableColors = uncoloredCopyGraphNeighbours.map { it.availableColors() intersect register.availableColors() }
        return commonAvailableColors.flatten().groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
    }

    // ----------- create result ------------------

    private fun createAllocationResult(): Allocation.AllocationResult = Allocation.AllocationResult(
        allocationMap.flatMap { (node, reg) -> node.registers.associateWith { reg }.toList() }.toMap(),
        spilledRegisters.flatMap { it.registers }
    )
}
