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
                if (this[it]!! != other[it]) return false
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
val <K, V> ReferenceMap<K, V>.referenceKeys get() = referenceHashSetOf(referenceEntries.map { it.key })

fun <K, V> combineReferenceMaps(maps: List<ReferenceMap<K, V>>): ReferenceMap<K, V> {
    val combinedMaps = referenceHashMapOf<K, V>()
    maps.forEach { combinedMaps.putAll(it) }
    return combinedMaps
}

fun <K, V> referenceHashMapOf(pairs: List<Pair<K, V>>): ReferenceHashMap<K, V> {
    val map = ReferenceHashMap<K, V>()
    for ((key, value) in pairs)
        map[key] = value
    return map
}
fun <K, V> referenceMapOf(pairs: List<Pair<K, V> >): ReferenceMap<K, V> = referenceHashMapOf(pairs)

fun <K, V> combineReferenceMaps(vararg maps: ReferenceMap<K, V>): ReferenceMap<K, V> = combineReferenceMaps(maps.asList())

fun <K, V> referenceHashMapOf(vararg pairs: Pair<K, V>): ReferenceHashMap<K, V> = referenceHashMapOf(pairs.asList())

fun <K, V> ReferenceHashMap<K, V>.copy() = ReferenceHashMap<K, V>().also { it.putAll(this) }
