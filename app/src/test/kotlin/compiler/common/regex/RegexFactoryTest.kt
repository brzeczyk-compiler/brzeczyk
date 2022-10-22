package compiler.common.regex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

private val ATOMIC_AB = RegexFactory.createAtomic(setOf('a', 'b'))
private val ATOMIC_BC = RegexFactory.createAtomic(setOf('c', 'b'))
private val ATOMIC_ABC = RegexFactory.createAtomic(setOf('c', 'a', 'b'))
private val CONCAT_AB = RegexFactory.createConcat(RegexFactory.createAtomic(setOf('a')), RegexFactory.createAtomic(setOf('b')))
private val CONCAT_AC = RegexFactory.createConcat(RegexFactory.createAtomic(setOf('a')), RegexFactory.createAtomic(setOf('c')))
private val CONCAT_BC = RegexFactory.createConcat(RegexFactory.createAtomic(setOf('b')), RegexFactory.createAtomic(setOf('c')))
private val CONCAT_CD = RegexFactory.createConcat(RegexFactory.createAtomic(setOf('c')), RegexFactory.createAtomic(setOf('d')))
private val EMPTY = RegexFactory.createEmpty<Char>()
private val EPSILON = RegexFactory.createEpsilon<Char>()

class RegexFactoryTest {
    private fun <T> assertAllEqual(elements: List<T>) {
        if (elements.isEmpty()) return
        elements.forEach {
            assertEquals(it, elements[0])
            assertEquals(it.hashCode(), elements[0].hashCode())
        }
    }

    private fun <A : Comparable<A>> assertEqualsAndHashCodeMatches(regex1: Regex<A>, regex2: Regex<A>) {
        assertEquals(regex1, regex2)
        assertEquals(regex1.hashCode(), regex2.hashCode())
    }

    @Test fun `test concat preserves order`() {
        assertEqualsAndHashCodeMatches(RegexFactory.createConcat(ATOMIC_BC, ATOMIC_AB), Regex.Concat(ATOMIC_BC, ATOMIC_AB))
    }

    @Test fun `test concat handles empty set`() {
        assertEqualsAndHashCodeMatches(RegexFactory.createConcat(CONCAT_AB, EMPTY), EMPTY)
        assertEqualsAndHashCodeMatches(RegexFactory.createConcat(EMPTY, CONCAT_AB), EMPTY)
    }

    @Test fun `test concat handles epsilon`() {
        assertEqualsAndHashCodeMatches(RegexFactory.createConcat(CONCAT_AB, EPSILON), CONCAT_AB)
        assertEqualsAndHashCodeMatches(RegexFactory.createConcat(EPSILON, CONCAT_AB), CONCAT_AB)
    }

    @Test fun `test concat is associative`() {
        val concat1 = RegexFactory.createConcat(CONCAT_AB, CONCAT_AC)
        val concat2 = RegexFactory.createConcat(CONCAT_AC, CONCAT_BC)
        assertEqualsAndHashCodeMatches(RegexFactory.createConcat(concat1, CONCAT_BC), RegexFactory.createConcat(CONCAT_AB, concat2))
    }

    @Test fun `test concat is not commutative`() {
        assertNotEquals(RegexFactory.createConcat(ATOMIC_AB, ATOMIC_BC), RegexFactory.createConcat(ATOMIC_BC, ATOMIC_AB))
    }

    @Test fun `test long concats are associative`() {
        // from theoretical point of view, the previous test should be sufficient
        // however, practically, it is very easy to write code that only works for the shortest case
        val concatLeft = RegexFactory.createConcat(
            RegexFactory.createConcat(
                RegexFactory.createConcat(
                    CONCAT_AB,
                    CONCAT_AC
                ),
                CONCAT_BC
            ),
            CONCAT_CD
        )
        val concatRight = RegexFactory.createConcat(
            CONCAT_AB,
            RegexFactory.createConcat(
                CONCAT_AC,
                RegexFactory.createConcat(CONCAT_BC, CONCAT_CD)
            )
        )
        val concatSymmetrical = RegexFactory.createConcat(
            RegexFactory.createConcat(CONCAT_AB, CONCAT_AC),
            RegexFactory.createConcat(CONCAT_BC, CONCAT_CD)
        )
        val concatMixed = RegexFactory.createConcat(
            RegexFactory.createConcat(
                CONCAT_AB,
                RegexFactory.createConcat(CONCAT_AC, CONCAT_BC)
            ),
            CONCAT_CD
        )

        assertAllEqual(listOf(concatLeft, concatRight, concatSymmetrical, concatMixed))
    }

    @Test fun `test star operator is collapsable`() {
        val starAtomicAb = RegexFactory.createStar(CONCAT_AB)
        assertEqualsAndHashCodeMatches(RegexFactory.createStar(starAtomicAb), starAtomicAb)
        assertEqualsAndHashCodeMatches(RegexFactory.createStar(RegexFactory.createStar(starAtomicAb)), starAtomicAb)
    }

    @Test fun `test epsilon star is epsilon`() {
        assertEqualsAndHashCodeMatches(RegexFactory.createStar(EPSILON), EPSILON)
    }

    @Test fun `test empty star is epsilon`() {
        assertEqualsAndHashCodeMatches(RegexFactory.createStar(EMPTY), EPSILON)
    }

    @Test fun `test union handles empty set`() {
        assertEqualsAndHashCodeMatches(RegexFactory.createUnion(CONCAT_AB, EMPTY), CONCAT_AB)
        assertEqualsAndHashCodeMatches(RegexFactory.createUnion(EMPTY, CONCAT_AB), CONCAT_AB)
    }

    @Test fun `test union is associative`() {
        val union1 = RegexFactory.createUnion(CONCAT_AB, CONCAT_AC)
        val union2 = RegexFactory.createUnion(CONCAT_AC, CONCAT_BC)
        assertEqualsAndHashCodeMatches(RegexFactory.createUnion(union1, CONCAT_BC), RegexFactory.createUnion(CONCAT_AB, union2))
    }

    @Test fun `test long unions are associative`() {
        // from theoretical point of view, the previous test should be sufficient
        // however, practically, it is very easy to write code that only works for the shortest case
        val unionLeft = RegexFactory.createUnion(
            RegexFactory.createUnion(
                RegexFactory.createUnion(
                    CONCAT_AB,
                    CONCAT_AC
                ),
                CONCAT_BC
            ),
            CONCAT_CD
        )
        val unionRight = RegexFactory.createUnion(
            CONCAT_AB,
            RegexFactory.createUnion(
                CONCAT_AC,
                RegexFactory.createUnion(CONCAT_BC, CONCAT_CD)
            )
        )
        val unionSymmetrical = RegexFactory.createUnion(
            RegexFactory.createUnion(CONCAT_AB, CONCAT_AC),
            RegexFactory.createUnion(CONCAT_BC, CONCAT_CD)
        )
        val unionMixed = RegexFactory.createUnion(
            RegexFactory.createUnion(
                CONCAT_AB,
                RegexFactory.createUnion(CONCAT_AC, CONCAT_BC)
            ),
            CONCAT_CD
        )
        assertAllEqual(listOf(unionLeft, unionRight, unionSymmetrical, unionMixed))
    }

    @Test fun `test union is commutative`() {
        assertEqualsAndHashCodeMatches(
            RegexFactory.createUnion(CONCAT_AB, CONCAT_AC),
            RegexFactory.createUnion(CONCAT_AC, CONCAT_AB)
        )
    }

    @Test fun `test long unions are commutative`() {
        // from theoretical point of view, the previous test should be sufficient
        // however, practically, it is very easy to write code that only works for the shortest case
        val unionAB_AC_BC = RegexFactory.createUnion(
            RegexFactory.createUnion(CONCAT_AB, CONCAT_AC),
            CONCAT_BC
        )
        val unionAB_BC_AC = RegexFactory.createUnion(
            RegexFactory.createUnion(CONCAT_AB, CONCAT_BC),
            CONCAT_AC
        )
        val unionAC_AB_BC = RegexFactory.createUnion(
            RegexFactory.createUnion(CONCAT_AC, CONCAT_AB),
            CONCAT_BC
        )
        val unionAC_BC_AB = RegexFactory.createUnion(
            RegexFactory.createUnion(CONCAT_AC, CONCAT_BC),
            CONCAT_AB
        )
        val unionBC_AB_AC = RegexFactory.createUnion(
            RegexFactory.createUnion(CONCAT_BC, CONCAT_AB),
            CONCAT_AC
        )
        val unionBC_AC_AB = RegexFactory.createUnion(
            RegexFactory.createUnion(CONCAT_BC, CONCAT_AC),
            CONCAT_AB
        )
        assertAllEqual(listOf(unionAB_AC_BC, unionAB_BC_AC, unionAC_AB_BC, unionAC_BC_AB, unionBC_AB_AC, unionBC_AC_AB))
    }

    @Test fun `test union removes repetitions`() {
        assertEqualsAndHashCodeMatches(RegexFactory.createUnion(CONCAT_AB, CONCAT_AB), CONCAT_AB)
    }

    @Test fun `test nested union removes repetitions`() {
        // from theoretical point of view, the previous test should be sufficient
        // however, practically, it is very easy to write code that only works for the simplest case
        val nestedUnionWithRepetition = RegexFactory.createUnion(
            RegexFactory.createUnion(CONCAT_AB, CONCAT_BC),
            RegexFactory.createUnion(CONCAT_BC, CONCAT_AB)
        )
        val unionWithoutRepetitions = RegexFactory.createUnion(CONCAT_AB, CONCAT_BC)
        assertEqualsAndHashCodeMatches(nestedUnionWithRepetition, unionWithoutRepetitions)
    }

    @Test fun `test union merges atomics`() {
        assertEqualsAndHashCodeMatches(RegexFactory.createUnion(ATOMIC_AB, ATOMIC_BC), ATOMIC_ABC)
    }

    @Test fun `test complex union merges atomics`() {
        // from theoretical point of view, the previous test should be sufficient
        // however, practically, it is very easy to write code that only works for the simplest case
        val oneAtomicHidden = RegexFactory.createUnion(
            ATOMIC_AB,
            RegexFactory.createUnion(
                ATOMIC_BC,
                RegexFactory.createUnion(CONCAT_BC, CONCAT_AB)
            )
        )
        val bothAtomicsHidden = RegexFactory.createUnion(
            RegexFactory.createUnion(ATOMIC_AB, CONCAT_BC),
            RegexFactory.createUnion(CONCAT_AB, ATOMIC_BC)
        )
        val atomicsPremerged = RegexFactory.createUnion(ATOMIC_ABC, RegexFactory.createUnion(CONCAT_BC, CONCAT_AB))

        assertAllEqual(listOf(oneAtomicHidden, bothAtomicsHidden, atomicsPremerged))
    }
}
