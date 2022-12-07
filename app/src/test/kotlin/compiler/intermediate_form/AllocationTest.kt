package compiler.intermediate_form

import compiler.common.Indexed
import kotlin.test.Test
import kotlin.test.assertTrue

class AllocationTest {

    class AllocationTestWithData(
        val accessibleRegisters: List<Register>,
        interferenceList: List<Pair<Int, Int>>,
        copyList: List<Pair<Int, Int>> = emptyList(),
    ) {
        private val verticesToRegisters = (interferenceList.flatMap { setOf(it.first, it.second) }).associateWith { Register() }
        val liveness = Liveness.LivenessGraphs(
            interferenceList.asMap(),
            copyList.asMap()
        )
        val result = Allocation.allocateRegisters(
            emptyList(),
            liveness,
            accessibleRegisters
        )

        val register
            get() = object : Indexed<Int, Register> {
                override fun get(a: Int): Register = verticesToRegisters[a]!!
            }
        val color
            get() = object : Indexed<Any, Register?> {
                override fun get(a: Any): Register? = when (a) {
                    is Int -> result.allocatedRegisters[register[a]]
                    is Register -> result.allocatedRegisters[a]
                    else -> throw IllegalArgumentException("'color' property requires Int or String as its argument.")
                }
            }

        private fun List<Pair<Int, Int>>.asMap(): Map<Register, Set<Register>> =
            groupBy({ register[it.first] }, { register[it.second] }).mapValues { it.value.toMutableSet() }.toMutableMap().apply {
                forEach { (key, value) ->
                    value.forEach { getOrPut(it) { HashSet() }.add(key) }
                }
            }
    }

    private fun AllocationTestWithData.assertProperColoring() {
        liveness.interferenceGraph.forEach { (node, neighbours) ->
            neighbours.forEach { neighbour ->
                assertTrue(color[node] == null || color[neighbour] == null || (color[node] != color[neighbour]))
            }
        }
    }

    private fun AllocationTestWithData.assertFullColoring() {
        assertProperColoring()
        assertTrue(liveness.interferenceGraph.keys.all { color[it] != null })
    }

    @Test
    fun `star test`(): Unit =
        AllocationTestWithData(
            listOf(Register.RAX, Register.RBX),
            listOf(
                1 to 2,
                1 to 3,
                1 to 4,
                1 to 5,
                1 to 6
            ),
        ).run {
            assertFullColoring()
        }
}
