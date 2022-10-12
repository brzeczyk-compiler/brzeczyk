package compiler.lexer.regex

sealed class Regex {

// -------------------------------- Interface --------------------------------

    abstract fun containsEpsilon(): Boolean

    abstract fun derivative(a: Char): Regex

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
    }
}
