/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package compiler.lexer.regex

import compiler.common.regex.Regex
import compiler.common.regex.RegexFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val ATOMIC_AB = Regex.Atomic(setOf('a', 'b'))
private val ATOMIC_AC = Regex.Atomic(setOf('a', 'c'))
private val EMPTY = Regex.Empty<Char>()
private val EPSILON = Regex.Epsilon<Char>()
private val CONCAT_EP_EM = Regex.Concat(EPSILON, EMPTY)
private val CONCAT_EM_EP = Regex.Concat(EMPTY, EPSILON)
private val STAR_AB = Regex.Star(ATOMIC_AB)
private val UNION_EP_EM = Regex.Union(EPSILON, EMPTY)
private val UNION_EM_EP = Regex.Union(EMPTY, EPSILON)

class RegexTest {
    @Test fun `test empty does not contain epsilon`() {
        val reg = Regex.Empty<Char>()
        assertFalse(reg.containsEpsilon())
    }

    @Test fun `test epsilon contains epsilon`() {
        val reg = Regex.Epsilon<Char>()
        assertTrue(reg.containsEpsilon())
    }

    @Test fun `test atomic does not contain epsilon`() {
        val reg = Regex.Atomic(setOf('a', 'b', 'c'))
        assertFalse(reg.containsEpsilon())
    }

    @Test fun `test star contains epsilon`() {
        val reg = Regex.Star(Regex.Empty<Char>())
        assertTrue(reg.containsEpsilon())
    }

    @Test fun `test unions with epsilon`() {
        val reg1 = Regex.Union(
            Regex.Epsilon(),
            Regex.Atomic(setOf('d', 'e', 'f')),
        )
        val reg2 = Regex.Union(
            Regex.Atomic(setOf('d', 'e', 'f')),
            Regex.Star(Regex.Empty()),
        )

        assertTrue(reg1.containsEpsilon())
        assertTrue(reg2.containsEpsilon())
    }

    @Test fun `test unions with no epsilon`() {
        val reg1 = Regex.Union(
            Regex.Atomic(setOf('a', 'b', 'c')),
            Regex.Atomic(setOf('d', 'e', 'f')),
        )
        val reg2 = Regex.Union(
            Regex.Concat(Regex.Epsilon(), Regex.Atomic(setOf('x'))),
            Regex.Concat(Regex.Atomic(setOf('y')), Regex.Star(Regex.Empty())),
        )

        assertFalse(reg1.containsEpsilon())
        assertFalse(reg2.containsEpsilon())
    }

    @Test fun `test concats with epsilon`() {
        val reg1 = Regex.Concat<Char>(
            Regex.Epsilon(),
            Regex.Epsilon(),
        )
        val reg2 = Regex.Concat(
            Regex.Star(Regex.Atomic(setOf('q'))),
            Regex.Union(Regex.Epsilon(), Regex.Atomic(setOf('w')))
        )

        assertTrue(reg1.containsEpsilon())
        assertTrue(reg2.containsEpsilon())
    }

    @Test fun `test concats with no epsilon`() {
        val reg1 = Regex.Concat(
            Regex.Epsilon(),
            Regex.Atomic(setOf('d', 'e', 'f')),
        )
        val reg2 = Regex.Concat<Char>(
            Regex.Empty(),
            Regex.Star(Regex.Empty()),
        )

        assertFalse(reg1.containsEpsilon())
        assertFalse(reg2.containsEpsilon())
    }

    @Test fun `test derivative of empty is empty`() {
        val reg = RegexFactory.createEmpty<Char>()
        assertTrue(reg.derivative('a') is Regex.Empty)
    }

    @Test fun `test derivative of epsilon is empty`() {
        val reg = RegexFactory.createEpsilon<Char>()
        assertTrue(reg.derivative('a') is Regex.Empty)
    }

    @Test fun `test derivative of atomic with proper atom is epsilon`() {
        val reg = RegexFactory.createAtomic(setOf('a'))
        assertTrue(reg.derivative('a') is Regex.Epsilon)
    }

    @Test fun `test derivative of atomic with no proper atom is empty`() {
        val reg = RegexFactory.createAtomic(setOf('a'))
        assertTrue(reg.derivative('b') is Regex.Empty)
    }

    private fun <T : Comparable<T>> assertOrdered(desiredOrder: List<T>) {
        for (i in 0..(desiredOrder.size - 1)) {
            for (j in 0..(desiredOrder.size - 1)) {
                if (i > j) assertTrue(desiredOrder[i] > desiredOrder[j])
                else if (i < j) assertTrue(desiredOrder[i] < desiredOrder[j])
            }
        }
    }

    private fun <T> assertAllNotEqual(elements: List<T>) {
        for (i in 0..(elements.size - 1)) {
            for (j in 0..(elements.size - 1)) {
                if (i != j) assertNotEquals(elements[i], elements[j])
            }
        }
    }

    private fun <T> assertEqualsWellDefined(controlElement: T, equalElement: T, notEqualElement: T) {
        assertEquals(controlElement, equalElement)
        assertEquals(controlElement.hashCode(), equalElement.hashCode())
        assertNotEquals(controlElement, notEqualElement)
    }

    @Test fun `test regexes of different kind are not equal`() {
        assertAllNotEqual(listOf(ATOMIC_AB, CONCAT_EP_EM, EMPTY, EPSILON, UNION_EP_EM, STAR_AB))
    }

    @Test fun `test equals operator is well defined for regexes of same kind`() {
        assertEquals<Regex<Char>>(EMPTY, Regex.Empty())
        assertEquals(EMPTY.hashCode(), Regex.Empty<Char>().hashCode())

        assertEquals<Regex<Char>>(EPSILON, Regex.Epsilon())
        assertEquals(EPSILON.hashCode(), Regex.Epsilon<Char>().hashCode())

        assertEqualsWellDefined(ATOMIC_AB, Regex.Atomic(setOf('a', 'b')), ATOMIC_AC)

        assertEqualsWellDefined(STAR_AB, Regex.Star(ATOMIC_AB), Regex.Star(EMPTY))

        assertEqualsWellDefined(UNION_EP_EM, Regex.Union(EPSILON, EMPTY), UNION_EM_EP)

        assertEqualsWellDefined(CONCAT_EP_EM, Regex.Concat(EPSILON, EMPTY), CONCAT_EM_EP)
    }

    @Test fun `test regexes are sorted lexicographically by type`() {
        // also ensures atomic is the smallest, which is important for us
        assertOrdered(listOf(ATOMIC_AB, CONCAT_EP_EM, EMPTY, EPSILON, STAR_AB, UNION_EP_EM))
    }

    @Test fun `test atomics are sorted lexicographically by chars`() {
        assertOrdered(listOf(Regex.Atomic(setOf('a')), ATOMIC_AB, ATOMIC_AC))
    }

    @Test fun `test concats are sorted lexicographically by children`() {
        assertOrdered(listOf(CONCAT_EM_EP, Regex.Concat(EPSILON, ATOMIC_AB), Regex.Concat(EPSILON, ATOMIC_AC), CONCAT_EP_EM))
    }

    @Test fun `test stars are sorted by children`() {
        assertOrdered(listOf(STAR_AB, Regex.Star(ATOMIC_AC), Regex.Star(EPSILON)))
    }

    @Test fun `test unions are sorted lexicographically by children`() {
        assertOrdered(listOf(UNION_EM_EP, Regex.Union(EPSILON, ATOMIC_AB), Regex.Union(EPSILON, ATOMIC_AC), UNION_EP_EM))
    }

    @Test fun `test atomics comparisons work for strings`() {
        val A: Regex<String> = Regex.Atomic(setOf("ab", "c"))
        val B: Regex<String> = Regex.Atomic(setOf("a", "bc"))
        assertTrue(A > B)
    }
}
