package compiler.utils

class Ref<out T>(val value: T) {
    override fun equals(other: Any?): Boolean = other is Ref<*> && other.value === value

    override fun hashCode(): Int = System.identityHashCode(value)

    override fun toString(): String = value.toString()
}

typealias RefSet<E> = Set<Ref<E>>

typealias RefMap<K, V> = Map<Ref<K>, Ref<V>>

typealias KeyRefMap<K, V> = Map<Ref<K>, V>

typealias MutableRefSet<E> = MutableSet<Ref<E>>

typealias MutableRefMap<K, V> = MutableMap<Ref<K>, Ref<V>>

typealias MutableKeyRefMap<K, V> = MutableMap<Ref<K>, V>

fun <E> refSetOf(vararg elements: E): RefSet<E> = mutableRefSetOf(*elements)

fun <K, V> refMapOf(vararg entries: Pair<K, V>): RefMap<K, V> = mutableRefMapOf(*entries)

fun <K, V> keyRefMapOf(vararg entries: Pair<K, V>): KeyRefMap<K, V> = mutableKeyRefMapOf(*entries)

fun <E> mutableRefSetOf(vararg elements: E): MutableRefSet<E> {
    val set = mutableSetOf<Ref<E>>()

    for (element in elements)
        set.add(Ref(element))

    return set
}

fun <K, V> mutableRefMapOf(vararg entries: Pair<K, V>): MutableRefMap<K, V> {
    val map = mutableMapOf<Ref<K>, Ref<V>>()

    for ((key, value) in entries) {
        map[Ref(key)] = Ref(value)
    }

    return map
}

fun <K, V> mutableKeyRefMapOf(vararg entries: Pair<K, V>): MutableKeyRefMap<K, V> {
    val map = mutableMapOf<Ref<K>, V>()

    for ((key, value) in entries) {
        map[Ref(key)] = value
    }

    return map
}
