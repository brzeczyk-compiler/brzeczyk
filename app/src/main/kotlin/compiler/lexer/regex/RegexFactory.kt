package compiler.lexer.regex

object RegexFactory {

    fun createEmpty(): Regex {
        return Regex.Empty()
    }

    fun createEpsilon(): Regex {
        return Regex.Epsilon()
    }

    fun createAtomic(atoms: Set<Char>): Regex {
        return Regex.Atomic(atoms)
    }

    fun createStar(child: Regex): Regex {
        if (child is Regex.Empty || child is Regex.Epsilon) {
            return Regex.Epsilon()
        }
        if (child is Regex.Star) {
            return Regex.Star(child.child)
        }
        return Regex.Star(child)
    }

    fun createUnion(left: Regex, right: Regex): Regex {
        // we maintain two invariants:
        // right child is never a Union
        // nested Unions are sorted (with the smallest element being the leftmost one)
        if (left == right) return left
        if (left is Regex.Empty || right is Regex.Empty) {
            return Regex.Empty()
        }
        if (left is Regex.Atomic && right is Regex.Atomic) {
            return Regex.Atomic(left.atomic + right.atomic)
        }
        fun goLeft(regex: Regex): Sequence<Regex> {
            return sequence {
                if (regex !is Regex.Union) {
                    yield(regex)
                } else {
                    yieldAll(goLeft(regex.left))
                    yield(regex.right)
                }
            }
        }
        val elemsSorted = sequence {
            val leftElemsSorted = goLeft(left).iterator()
            val rightElemsSorted = goLeft(right).iterator()
            var currLeft = leftElemsSorted.next()
            var currRight = rightElemsSorted.next()
            while (true) {
                if (currLeft < currRight) {
                    yield(currLeft)
                    if (leftElemsSorted.hasNext()) {
                        currLeft = leftElemsSorted.next()
                        continue
                    }
                    yield(currRight)
                    yieldAll(rightElemsSorted)
                    break
                } else {
                    yield(currRight)
                    if (rightElemsSorted.hasNext()) {
                        currRight = rightElemsSorted.next()
                        continue
                    }
                    yield(currLeft)
                    yieldAll(leftElemsSorted)
                    break
                }
            }
        }.iterator()
        val initialUnion = Regex.Union(elemsSorted.next(), elemsSorted.next())
        return elemsSorted.asSequence().fold(initialUnion) { union, elem -> Regex.Union(union, elem) }
    }

    fun createConcat(left: Regex, right: Regex): Regex {
        // we maintain the invariant that the right child of Concat is not a Concat
        // TODO: normalize
        if (left is Regex.Empty || right is Regex.Empty) {
            return Regex.Empty()
        }
        if (left is Regex.Epsilon) {
            return right
        }
        if (right is Regex.Epsilon) {
            return left
        }
        fun addLeftmostChild(concat: Regex.Concat, child: Regex): Regex.Concat {
            val updatedLeftChild = if (concat.left is Regex.Concat) addLeftmostChild(concat.left, child)
            else Regex.Concat(child, concat.left)
            return Regex.Concat(updatedLeftChild, concat.right)
        }
        if (right is Regex.Concat) {
            return addLeftmostChild(right, left)
        }
        return Regex.Concat(left, right)
    }
}
