package compiler.common.semantic_analysis

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
