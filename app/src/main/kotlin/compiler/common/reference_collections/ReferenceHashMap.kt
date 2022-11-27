package compiler.common.reference_collections

import java.util.IdentityHashMap

interface ReferenceMap<K, V> : Map<K, V>

interface MutableReferenceMap<K, V> : MutableMap<K, V>, ReferenceMap<K, V>

class ReferenceHashMap<K, V> : IdentityHashMap<K, V>(), MutableReferenceMap<K, V> {
    override fun equals(other: Any?): Boolean {
        if (other is ReferenceHashMap<*, *>) {
            if (this.size != other.size) return false
            this.keys.forEach {
                if (!other.containsKey(it)) return false
                if (!this[it]!!.equals(other[it])) return false
            }
            return true
        }
        return false
    }
}

class ReferenceEntry<K, V>(val key: K, var value: V) {
    override fun equals(other: Any?): Boolean = other is ReferenceEntry<*, *> && key === other.key && value == other.value

    override fun hashCode() = Pair(System.identityHashCode(key), value).hashCode()

    override fun toString() = "$key to $value"
}

val <K, V> ReferenceMap<K, V>.referenceEntries get() = entries.map { ReferenceEntry(it.key, it.value) }.toSet()
val <K, V> ReferenceMap<K, V>.referenceKeys get() = referenceSetOf(referenceEntries.map { it.key })
val <K, V> ReferenceMap<K, V>.referenceValues get() = referenceSetOf(referenceEntries.map { it.value })

fun <K, V> referenceMapOf(pairs: List<Pair<K, V>>): ReferenceMap<K, V> {
    val map = ReferenceHashMap<K, V>()
    for ((key, value) in pairs)
        map[key] = value
    return map
}

fun <K, V> referenceMapOf(vararg pairs: Pair<K, V>): ReferenceMap<K, V> = referenceMapOf(pairs.asList())
