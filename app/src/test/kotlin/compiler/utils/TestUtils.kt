package compiler.utils

import kotlin.test.assertContains

fun <E> assertContentEquals(expected: Set<E>, actual: Set<E>) {
    for (element in expected)
        assertContains(actual, element)
    for (element in actual)
        assertContains(expected, element)
}

fun <K, V> assertContentEquals(expected: Map<K, V>, actual: Map<K, V>) {
    assertContentEquals(expected.entries, actual.entries)
}
