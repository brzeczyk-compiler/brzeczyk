package compiler.common.semantic_analysis

import java.util.IdentityHashMap

interface ReferenceMap<K, V> : Map<K, V>
interface MutableReferenceMap<K, V> : MutableMap<K, V>, ReferenceMap<K, V>
class ReferenceHashMap<K, V> : IdentityHashMap<K, V>(), MutableReferenceMap<K, V>
