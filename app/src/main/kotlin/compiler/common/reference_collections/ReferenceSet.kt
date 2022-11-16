package compiler.common.reference_collections

import java.util.Collections

interface ReferenceSet<E> : Set<E>

interface MutableReferenceSet<E> : MutableSet<E>, ReferenceSet<E>

class ReferenceHashSet<E> : MutableSet<E> by Collections.newSetFromMap(ReferenceHashMap()), MutableReferenceSet<E>
