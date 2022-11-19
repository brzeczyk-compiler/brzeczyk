package compiler.common.reference_collections

import java.util.Collections

interface ReferenceSet<E> : Set<E>

interface MutableReferenceSet<E> : MutableSet<E>, ReferenceSet<E>

class ReferenceHashSet<E> : MutableSet<E> by Collections.newSetFromMap(ReferenceHashMap()), MutableReferenceSet<E>

class ReferenceElement<E>(val element: E) {
    override fun equals(other: Any?): Boolean = other is ReferenceElement<*> && element === other.element

    override fun hashCode() = System.identityHashCode(element)

    override fun toString() = element.toString()
}

val <E> ReferenceSet<E>.referenceElements get() = this.map { ReferenceElement(it) }.toSet()

fun <E> combineReferenceSets(sets: List<ReferenceSet<E>>): ReferenceSet<E> {
    val combinedSets = ReferenceHashSet<E>()
    sets.forEach { combinedSets.addAll(it) }
    return combinedSets
}

fun <E> referenceSetOf(elements: List<E>): ReferenceSet<E> {
    val set = ReferenceHashSet<E>()
    set.addAll(elements)
    return set
}

fun <E> combineReferenceSets(vararg sets: ReferenceSet<E>): ReferenceSet<E> = combineReferenceSets(sets.asList())

fun <E> referenceSetOf(vararg elements: E): ReferenceSet<E> = referenceSetOf(elements.asList())
