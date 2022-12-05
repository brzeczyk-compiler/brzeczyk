package compiler.common.reference_collections

import java.util.Collections

interface ReferenceSet<E> : Set<E>

interface MutableReferenceSet<E> : MutableSet<E>, ReferenceSet<E>

class ReferenceHashSet<E> : MutableSet<E> by Collections.newSetFromMap(ReferenceHashMap()), MutableReferenceSet<E> {
    override fun equals(other: Any?): Boolean = other is ReferenceHashSet<*> && this.referenceElements == other.referenceElements
}

class ReferenceElement<E>(val element: E) {
    override fun equals(other: Any?): Boolean = other is ReferenceElement<*> && element === other.element

    override fun hashCode() = System.identityHashCode(element)

    override fun toString() = element.toString()
}

val <E> ReferenceSet<E>.referenceElements get() = this.map { ReferenceElement(it) }.toSet()

fun <E> combineReferenceSets(sets: List<ReferenceSet<E>>): ReferenceSet<E> {
    val combinedSets = referenceHashSetOf<E>()
    sets.forEach { combinedSets.addAll(it) }
    return combinedSets
}

fun <E> referenceHashSetOf(elements: List<E>): ReferenceHashSet<E> {
    val set = ReferenceHashSet<E>()
    set.addAll(elements)
    return set
}

fun <E> combineReferenceSets(vararg sets: ReferenceSet<E>): ReferenceSet<E> = combineReferenceSets(sets.asList())

fun <E> referenceHashSetOf(vararg elements: E): ReferenceHashSet<E> = referenceHashSetOf(elements.asList())
