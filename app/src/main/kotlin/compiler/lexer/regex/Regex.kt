package compiler.lexer.regex

sealed class Regex : Comparable<Regex> {

// -------------------------------- Interface --------------------------------

    abstract fun containsEpsilon(): Boolean

    abstract fun derivative(a: Char): Regex

    abstract override fun compareTo(other: Regex): Int

    abstract override fun equals(other: Any?): Boolean

// -------------------------------- Subclasses --------------------------------

    class Empty internal constructor() : Regex() {
        override fun containsEpsilon(): Boolean {
            return false
        }

        override fun derivative(a: Char): Regex {
            return RegexFactory.createEmpty()
        }
        override fun compareTo(other: Regex): Int {
            if (other !is Empty) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return 0
        }
        override fun equals(other: Any?): Boolean {
            return other is Empty
        }
    }

    class Epsilon internal constructor() : Regex() {
        override fun containsEpsilon(): Boolean {
            return true
        }

        override fun derivative(a: Char): Regex {
            return RegexFactory.createEmpty()
        }
        override fun compareTo(other: Regex): Int {
            if (other !is Epsilon) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return 0
        }
        override fun equals(other: Any?): Boolean {
            return other is Epsilon
        }
    }

    class Atomic internal constructor(val atomic: Set<Char>) : Regex() {
        override fun containsEpsilon(): Boolean {
            return false
        }

        override fun derivative(a: Char): Regex {
            return if (atomic.contains(a)) RegexFactory.createEpsilon() else RegexFactory.createEmpty()
        }
        override fun compareTo(other: Regex): Int {
            if (other !is Atomic) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            if (this.atomic == other.atomic) return 0
            if (this.atomic.size != other.atomic.size) return this.atomic.size.compareTo(other.atomic.size)
            for (pair in this.atomic.toSortedSet().zip(other.atomic.toSortedSet())) {
                val (ours, theirs) = pair
                if (ours != theirs) return ours.compareTo(theirs)
            }
            return 0
        }
        override fun equals(other: Any?): Boolean {
            if (other !is Atomic) return false
            return this.atomic == other.atomic
        }
    }

    class Star internal constructor(val child: Regex) : Regex() {
        override fun containsEpsilon(): Boolean {
            return true
        }

        override fun derivative(a: Char): Regex {
            return RegexFactory.createConcat(child.derivative(a), RegexFactory.createStar(child))
        }
        override fun compareTo(other: Regex): Int {
            if (other !is Star) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return this.child.compareTo(other.child)
        }
        override fun equals(other: Any?): Boolean {
            if (other !is Star) return false
            return this.child == other.child
        }
    }

    class Union internal constructor(val left: Regex, val right: Regex) : Regex() {
        override fun containsEpsilon(): Boolean {
            return left.containsEpsilon() || right.containsEpsilon()
        }

        override fun derivative(a: Char): Regex {
            return RegexFactory.createUnion(left.derivative(a), right.derivative(a))
        }
        override fun compareTo(other: Regex): Int {
            if (other !is Union) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return compareBy<Union>({ it.left }, { it.right }).compare(this, other)
        }
        override fun equals(other: Any?): Boolean {
            if (other !is Union) return false
            return this.left == other.left && this.right == other.right
        }
    }

    class Concat internal constructor(val left: Regex, val right: Regex) : Regex() {
        override fun containsEpsilon(): Boolean {
            return left.containsEpsilon() && right.containsEpsilon()
        }

        override fun derivative(a: Char): Regex {
            return if (left.containsEpsilon())
                RegexFactory.createUnion(RegexFactory.createConcat(left.derivative(a), right), right.derivative(a)) else
                RegexFactory.createConcat(left.derivative(a), right)
        }
        override fun compareTo(other: Regex): Int {
            if (other !is Concat) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return compareBy<Concat>({ it.left }, { it.right }).compare(this, other)
        }
        override fun equals(other: Any?): Boolean {
            if (other !is Concat) return false
            return this.left == other.left && this.right == other.right
        }
    }
}
