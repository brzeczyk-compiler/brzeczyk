package compiler.lowlevel.allocation

import compiler.intermediate.Register
import compiler.lowlevel.dataflow.Liveness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColoringAllocationTest {

    class AllocationTestWithData(
        accessibleRegisters: List<Register>,
        interferenceList: List<Pair<Any, Any>> = emptyList(),
        copyList: List<Pair<Any, Any>> = emptyList(),
        zeroDegreeNodes: List<Any> = emptyList()
    ) {
        val verticesToRegisters =
            (interferenceList.flatMap { setOf(it.first, it.second) } + copyList.flatMap { setOf(it.first, it.second) } + zeroDegreeNodes).toHashSet().associateWith {
                when (it) {
                    is Register -> it
                    else -> Register()
                }
            }

        val liveness = run {
            val interferenceGraph = interferenceList.asMap().toMutableMap()
            val copyGraph = copyList.asMap().toMutableMap()

            Liveness.LivenessGraphs(interferenceGraph, copyGraph)
        }
        val result = ColoringAllocation.allocateRegisters(
            liveness,
            accessibleRegisters
        )

        interface Indexed<T, K> {
            operator fun get(a: T): K
        }

        val register
            get() = object : Indexed<Any, Register> {
                override fun get(a: Any): Register = verticesToRegisters[a]!!
            }
        val color
            get() = object : Indexed<Any, Register?> {
                override fun get(a: Any): Register? = when (a) {
                    is Int -> result.allocatedRegisters[register[a]]
                    is Register -> result.allocatedRegisters[a]
                    else -> throw IllegalArgumentException("'color' property requires Int or String as its argument.")
                }
            }

        private fun List<Pair<Any, Any>>.asMap(): Map<Register, Set<Register>> =
            groupBy({ register[it.first] }, { register[it.second] }).mapValues { it.value.toMutableSet() }.toMutableMap()
                .apply {
                    verticesToRegisters.values.forEach { putIfAbsent(it, hashSetOf()) }
                }
                .apply {
                    forEach { (key, value) ->
                        value.forEach { get(it)!!.add(key) }
                    }
                }
    }

    private fun AllocationTestWithData.assertProperColoring() {

        assertEquals((result.allocatedRegisters.keys + result.spilledRegisters).toHashSet(), verticesToRegisters.values.toHashSet())
        assertTrue((result.allocatedRegisters.keys intersect result.spilledRegisters.toSet()).isEmpty())

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

    private fun cliqueOn(numbers: Iterable<Any>) = sequence {
        for (i in numbers) for (j in numbers) if (i != j) yield(i to j)
    }.toList()

    private fun fromToEach(start: Any, neighbours: Iterable<Any>) = neighbours.map { start to it }

    @Test
    fun `basic tests`() {
        AllocationTestWithData(
            listOf(Register.RAX),
            zeroDegreeNodes = listOf(1, 2)
        ).assertFullColoring()

        AllocationTestWithData(
            listOf(Register.RAX, Register.RBX),
            interferenceList = listOf(1 to 2)
        ).assertFullColoring()

        AllocationTestWithData(
            listOf(Register.RAX),
            copyList = listOf(1 to 2)
        ).assertFullColoring()

        AllocationTestWithData(
            listOf(Register.RAX),
            interferenceList = listOf(1 to 2),
            copyList = listOf(1 to 2)
        ).run {
            assertProperColoring()
            assertEquals(1, result.spilledRegisters.size)
        }
    }

    @Test
    fun `star test`(): Unit =
        AllocationTestWithData(
            listOf(Register.RAX, Register.RBX),
            fromToEach(1, 2..6)
        ).run {
            assertFullColoring()
        }

    @Test
    fun `spills test`() {
        AllocationTestWithData(
            listOf(Register.RAX, Register.RBX),
            interferenceList = listOf(
                1 to 2, 2 to 3, 3 to 1
            )
        ).run {
            assertProperColoring()
            assertEquals(1, result.spilledRegisters.size)
        }

        AllocationTestWithData(
            listOf(Register.RAX, Register.RBX),
            interferenceList = listOf(
                1 to 2, 2 to 3, 3 to 1, 1 to 4, 2 to 5, 3 to 6
            )
        ).run {
            assertProperColoring()
            assertEquals(1, result.spilledRegisters.size)
        }
    }

    @Test
    fun `system registers test`() {
        AllocationTestWithData(
            listOf(Register.RAX, Register.RBX),
            interferenceList = listOf(
                1 to Register.RAX
            )
        ).run {
            assertFullColoring()
            assertEquals(Register.RAX, color[Register.RAX])
        }

        AllocationTestWithData(
            listOf(Register.RAX, Register.RBX, Register.RCX),
            interferenceList = listOf(
                1 to 2, 2 to 3, 3 to 4, 4 to 1,
            ) + fromToEach(Register.RAX, 1..4)
        ).run {
            assertFullColoring()
            assertEquals(Register.RAX, color[Register.RAX])
        }
    }

    @Test
    fun `copy test`() {
        AllocationTestWithData(
            listOf(Register.RAX, Register.RBX),
            interferenceList = listOf(
                1 to 3, 3 to 2, 1 to 4
            ),
            copyList = listOf(
                1 to 2
            )
        ).run {
            assertFullColoring()
            assertEquals(color[1], color[2])
        }

        AllocationTestWithData(
            listOf(Register.RAX, Register.RBX),
            copyList = listOf(
                Register.RAX to Register.RBX
            )
        ).run {
            assertFullColoring()
            assertEquals(Register.RAX, color[Register.RAX])
            assertEquals(Register.RBX, color[Register.RBX])
        }

        AllocationTestWithData(
            listOf(Register.RAX, Register.RBX),
            interferenceList = cliqueOn(1..7) + fromToEach(Register.RAX, 1..6),
            copyList = listOf(
                7 to Register.RAX
            )
        ).run {
            assertProperColoring()
            assertEquals(Register.RAX, color[7])
        }

        val colors = (1..7).map { Register() }

        AllocationTestWithData(
            colors,
            interferenceList = cliqueOn(101..121) + cliqueOn(201..221) +
                cliqueOn(301..305) + // common neighbours of degree K
                fromToEach(1, 101..102) + fromToEach(1, 301..305) + // after merge, (1,2) has K-1 neighbours of degree > K
                fromToEach(2, 201..204) + fromToEach(2, 301..305),
            copyList = listOf(
                1 to 2
            )
        ).run {
            assertProperColoring()
            assertEquals(color[1], color[2])
        }
    }

    @Test
    fun `multiple copy test`() {
        val colors = (1..3).map { Register() }

        AllocationTestWithData(
            colors,
            interferenceList = cliqueOn(1..3) + cliqueOn(4..6),
            copyList = listOf(
                1 to 4, 2 to 5, 3 to 6
            )
        ).run {
            assertProperColoring()
            assertEquals(color[1], color[4])
            assertEquals(color[2], color[5])
            assertEquals(color[3], color[6])
        }
    }
}
