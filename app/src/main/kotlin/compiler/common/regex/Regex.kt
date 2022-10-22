package compiler.common.regex

import java.util.Objects
import kotlin.math.min

const val EQUALS = 0

sealed class Regex<A : Comparable<A>> : Comparable<Regex<A>> {
    // Regex parametrized by alphabet type <A>
    // the regexes of different type are sorted lexicographically by type
    // this means that Atomics are first, which is a fact on which we rely elsewhere!

// -------------------------------- Interface --------------------------------

    abstract fun containsEpsilon(): Boolean

    abstract fun first(): Set<A>

    abstract fun derivative(a: A): Regex<A>

    abstract override fun compareTo(other: Regex<A>): Int

    abstract override fun equals(other: Any?): Boolean

    abstract override fun hashCode(): Int

// -------------------------------- Subclasses --------------------------------

    class Empty<A : Comparable<A>> internal constructor() : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return false
        }

        override fun first(): Set<A> {
            return emptySet()
        }

        override fun derivative(a: A): Regex<A> {
            return RegexFactory.createEmpty()
        }

        override fun compareTo(other: Regex<A>): Int {
            if (other !is Empty) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return EQUALS
        }

        override fun equals(other: Any?): Boolean {
            return other is Empty<*>
        }

        override fun hashCode(): Int {
            // Not sure if this is the best option
            // Is there a way to make this class a singleton?
            return this.javaClass.hashCode()
        }

        override fun toString(): String {
            return "\u2205"
        }
    }

    class Epsilon<A : Comparable<A>> internal constructor() : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return true
        }

        override fun first(): Set<A> {
            return emptySet()
        }

        override fun derivative(a: A): Regex<A> {
            return RegexFactory.createEmpty()
        }

        override fun compareTo(other: Regex<A>): Int {
            if (other !is Epsilon) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return EQUALS
        }

        override fun equals(other: Any?): Boolean {
            return other is Epsilon<*>
        }

        override fun hashCode(): Int {
            // Not sure if this is the best option
            // Is there a way to make this class a singleton?
            return this.javaClass.hashCode()
        }
    }

    class Atomic<A : Comparable<A>> internal constructor(val atomic: Set<A>) : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return false
        }

        override fun first(): Set<A> {
            return atomic
        }

        override fun derivative(a: A): Regex<A> {
            return if (atomic.contains(a)) RegexFactory.createEpsilon() else RegexFactory.createEmpty()
        }

        override fun compareTo(other: Regex<A>): Int {
            if (other !is Atomic) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            val thisAtomicList = this.atomic.toSortedSet().toList()
            val otherAtomicList = other.atomic.toSortedSet().toList()
            for (i in 0 until min(thisAtomicList.size, otherAtomicList.size)) {
                compareValues(thisAtomicList[i], otherAtomicList[i]).let {
                    if (it != EQUALS) return it
                }
            }
            return compareValues(thisAtomicList.size, otherAtomicList.size)
        }

        override fun equals(other: Any?): Boolean {
            return other is Atomic<*> && (this.atomic == other.atomic)
        }

        override fun hashCode(): Int {
            return Objects.hash(atomic)
        }
    }

    class Star<A : Comparable<A>> internal constructor(val child: Regex<A>) : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return true
        }

        override fun first(): Set<A> {
            return child.first()
        }

        override fun derivative(a: A): Regex<A> {
            return RegexFactory.createConcat(child.derivative(a), RegexFactory.createStar(child))
        }

        override fun compareTo(other: Regex<A>): Int {
            if (other !is Star) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return this.child.compareTo(other.child)
        }

        override fun equals(other: Any?): Boolean {
            return other is Star<*> && (this.child == other.child)
        }

        override fun hashCode(): Int {
            // Is there a better way to make this hash different from that of child
            return Objects.hash(child, this.javaClass)
        }
    }

    class Union<A : Comparable<A>> internal constructor(val left: Regex<A>, val right: Regex<A>) : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return left.containsEpsilon() || right.containsEpsilon()
        }

        override fun first(): Set<A> {
            return left.first() union right.first()
        }

        override fun derivative(a: A): Regex<A> {
            return RegexFactory.createUnion(left.derivative(a), right.derivative(a))
        }

        override fun compareTo(other: Regex<A>): Int {
            if (other !is Union) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return compareBy<Union<A>>({ it.left }, { it.right }).compare(this, other)
        }

        override fun equals(other: Any?): Boolean {
            return other is Union<*> && (this.left == other.left && this.right == other.right)
        }

        override fun hashCode(): Int {
            // Is there a better way to make this hash different from that of concat(left, right)?
            return Objects.hash(left, right, this.javaClass)
        }
    }

    class Concat<A : Comparable<A>> internal constructor(val left: Regex<A>, val right: Regex<A>) : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return left.containsEpsilon() && right.containsEpsilon()
        }

        override fun first(): Set<A> {
            return if (left.containsEpsilon()) left.first() else left.first() union right.first()
        }

        override fun derivative(a: A): Regex<A> {
            return if (left.containsEpsilon())
                RegexFactory.createUnion(RegexFactory.createConcat(left.derivative(a), right), right.derivative(a)) else
                RegexFactory.createConcat(left.derivative(a), right)
        }

        override fun compareTo(other: Regex<A>): Int {
            if (other !is Concat) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return compareBy<Concat<A>>({ it.left }, { it.right }).compare(this, other)
        }

        override fun equals(other: Any?): Boolean {
            return other is Concat<*> && (this.left == other.left && this.right == other.right)
        }

        override fun hashCode(): Int {
            // Is there a better way to make this hash different from that of union(left, right)?
            return Objects.hash(left, right, this.javaClass)
        }
    }
}
