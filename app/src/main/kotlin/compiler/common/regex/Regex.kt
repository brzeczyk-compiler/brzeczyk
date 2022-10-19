package compiler.common.regex

const val EQUALS = 0

sealed class Regex<A : Comparable<A>> : Comparable<Regex<A>> {
    // Regex parametrized by alphabet type <A>
    // the regexes of different type are sorted lexicographically by type
    // this means that Atomics are first, which is a fact on which we rely elsewhere!

// -------------------------------- Interface --------------------------------

    abstract fun containsEpsilon(): Boolean

    abstract fun derivative(a: A): Regex<A>

    abstract override fun compareTo(other: Regex<A>): Int

    abstract override fun equals(other: Any?): Boolean

// -------------------------------- Subclasses --------------------------------

    class Empty<A : Comparable<A>> internal constructor() : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return false
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
    }

    class Epsilon<A : Comparable<A>> internal constructor() : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return true
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
    }

    class Atomic<A : Comparable<A>> internal constructor(val atomic: Set<A>) : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return false
        }

        override fun derivative(a: A): Regex<A> {
            return if (atomic.contains(a)) RegexFactory.createEpsilon() else RegexFactory.createEmpty()
        }

        override fun compareTo(other: Regex<A>): Int {
            if (other !is Atomic) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            val thisAtomicString = this.atomic.toSortedSet().joinToString("")
            val otherAtomicString = other.atomic.toSortedSet().joinToString("")
            return thisAtomicString.compareTo(otherAtomicString)
        }

        override fun equals(other: Any?): Boolean {
            return other is Atomic<*> && (this.atomic == other.atomic)
        }
    }

    class Star<A : Comparable<A>> internal constructor(val child: Regex<A>) : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return true
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
    }

    class Union<A : Comparable<A>> internal constructor(val left: Regex<A>, val right: Regex<A>) : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return left.containsEpsilon() || right.containsEpsilon()
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
    }

    class Concat<A : Comparable<A>> internal constructor(val left: Regex<A>, val right: Regex<A>) : Regex<A>() {
        override fun containsEpsilon(): Boolean {
            return left.containsEpsilon() && right.containsEpsilon()
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
    }
}
