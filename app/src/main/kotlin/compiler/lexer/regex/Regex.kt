package compiler.lexer.regex

val EQUALS = 0

sealed class Regex : Comparable<Regex> {
    // the regexes of different type are sorted lexicographically by type
    // this means that Atomics are first, which is a fact on which we rely elsewhere!

// -------------------------------- Interface --------------------------------

    abstract fun containsEpsilon(): Boolean

    abstract fun derivative(a: Char): Regex

    abstract override fun compareTo(other: Regex): Int

    abstract override fun equals(other: Any?): Boolean

// -------------------------------- Subclasses --------------------------------

    class Empty internal constructor() : Regex() {
        override fun containsEpsilon(): Boolean {
            // TODO
            return true
        }

        override fun derivative(a: Char): Regex {
            // TODO
            return this
        }

        override fun compareTo(other: Regex): Int {
            if (other !is Empty) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return EQUALS
        }

        override fun equals(other: Any?): Boolean {
            return other is Empty
        }
    }

    class Epsilon internal constructor() : Regex() {
        override fun containsEpsilon(): Boolean {
            // TODO
            return true
        }

        override fun derivative(a: Char): Regex {
            // TODO
            return this
        }

        override fun compareTo(other: Regex): Int {
            if (other !is Epsilon) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return EQUALS
        }

        override fun equals(other: Any?): Boolean {
            return other is Epsilon
        }
    }

    class Atomic internal constructor(val atomic: Set<Char>) : Regex() {
        override fun containsEpsilon(): Boolean {
            // TODO
            return true
        }

        override fun derivative(a: Char): Regex {
            // TODO
            return this
        }

        override fun compareTo(other: Regex): Int {
            if (other !is Atomic) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            val thisAtomicString = this.atomic.toSortedSet().joinToString("")
            val otherAtomicString = other.atomic.toSortedSet().joinToString("")
            return thisAtomicString.compareTo(otherAtomicString)
        }

        override fun equals(other: Any?): Boolean {
            return other is Atomic && (this.atomic == other.atomic)
        }
    }

    class Star internal constructor(val child: Regex) : Regex() {
        override fun containsEpsilon(): Boolean {
            // TODO
            return true
        }

        override fun derivative(a: Char): Regex {
            // TODO
            return this
        }

        override fun compareTo(other: Regex): Int {
            if (other !is Star) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return this.child.compareTo(other.child)
        }

        override fun equals(other: Any?): Boolean {
            return other is Star && (this.child == other.child)
        }
    }

    class Union internal constructor(val left: Regex, val right: Regex) : Regex() {
        override fun containsEpsilon(): Boolean {
            // TODO
            return true
        }

        override fun derivative(a: Char): Regex {
            // TODO
            return this
        }

        override fun compareTo(other: Regex): Int {
            if (other !is Union) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return compareBy<Union>({ it.left }, { it.right }).compare(this, other)
        }

        override fun equals(other: Any?): Boolean {
            return other is Union && (this.left == other.left && this.right == other.right)
        }
    }

    class Concat internal constructor(val left: Regex, val right: Regex) : Regex() {
        override fun containsEpsilon(): Boolean {
            // TODO
            return true
        }

        override fun derivative(a: Char): Regex {
            // TODO
            return this
        }

        override fun compareTo(other: Regex): Int {
            if (other !is Concat) {
                return this.javaClass.name.compareTo(other.javaClass.name)
            }
            return compareBy<Concat>({ it.left }, { it.right }).compare(this, other)
        }

        override fun equals(other: Any?): Boolean {
            return other is Concat && (this.left == other.left && this.right == other.right)
        }
    }
}
