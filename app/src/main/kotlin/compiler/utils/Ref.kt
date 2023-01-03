package compiler.utils

class Ref<out T>(val value: T) {
    override fun equals(other: Any?): Boolean = other is Ref<*> && other.value === value

    override fun hashCode(): Int = System.identityHashCode(value)

    override fun toString(): String = value.toString()
}

fun <E> refSetOf(vararg elements: E): Set<Ref<E>> = mutableRefSetOf(*elements)

fun <K, V> refMapOf(vararg entries: Pair<K, V>): Map<Ref<K>, Ref<V>> = mutableRefMapOf(*entries)

fun <K, V> keyRefMapOf(vararg entries: Pair<K, V>): Map<Ref<K>, V> = mutableKeyRefMapOf(*entries)

fun <E> mutableRefSetOf(vararg elements: E): MutableSet<Ref<E>> {
    val set = mutableSetOf<Ref<E>>()

    for (element in elements)
        set.add(Ref(element))

    return set
}

fun <K, V> mutableRefMapOf(vararg entries: Pair<K, V>): MutableMap<Ref<K>, Ref<V>> {
    val map = mutableMapOf<Ref<K>, Ref<V>>()

    for ((key, value) in entries) {
        map[Ref(key)] = Ref(value)
    }

    return map
}

fun <K, V> mutableKeyRefMapOf(vararg entries: Pair<K, V>): MutableMap<Ref<K>, V> {
    val map = mutableMapOf<Ref<K>, V>()

    for ((key, value) in entries) {
        map[Ref(key)] = value
    }

    return map
}
