package compiler.common.regex

object RegexFactory {

    fun <A : Comparable<A>> createEmpty(): Regex<A> {
        return Regex.Empty()
    }

    fun <A : Comparable<A>> createEpsilon(): Regex<A> {
        return Regex.Epsilon()
    }

    fun <A : Comparable<A>> createAtomic(atoms: Set<A>): Regex<A> {
        return Regex.Atomic(atoms)
    }

    fun <A : Comparable<A>> createStar(child: Regex<A>): Regex<A> {
        if (child is Regex.Empty || child is Regex.Epsilon) {
            return Regex.Epsilon()
        }
        if (child is Regex.Star) {
            return Regex.Star(child.child)
        }
        return Regex.Star(child)
    }

    fun <A : Comparable<A>> createUnion(left: Regex<A>, right: Regex<A>): Regex<A> {
        // we maintain five invariants:
        // right child of a Union is never a Union
        // nested Unions are sorted (with the smallest element being the leftmost one)
        // there is at most one atomic in Union (this is possible as we can freely merge atomics in Unions)
        // Empty cannot be a part of Union
        // there are no duplicates among all children of one Union
        if (left is Regex.Empty) {
            return right
        }
        if (right is Regex.Empty) {
            return left
        }
        fun traverseFromLeft(regex: Regex<A>): Sequence<Regex<A>> {
            return sequence {
                if (regex !is Regex.Union) {
                    yield(regex)
                } else {
                    yieldAll(traverseFromLeft(regex.left))
                    yield(regex.right)
                }
            }
        }
        val summandsSorted = sequence {
            val leftSummandsSorted = traverseFromLeft(left).iterator()
            val rightSummandsSorted = traverseFromLeft(right).iterator()
            var currentLeft = leftSummandsSorted.next()
            var currentRight = rightSummandsSorted.next()
            while (true) {
                if (currentLeft < currentRight) {
                    yield(currentLeft)
                    if (leftSummandsSorted.hasNext()) {
                        currentLeft = leftSummandsSorted.next()
                        continue
                    }
                    yield(currentRight)
                    yieldAll(rightSummandsSorted)
                    break
                } else if (currentRight < currentLeft) {
                    yield(currentRight)
                    if (rightSummandsSorted.hasNext()) {
                        currentRight = rightSummandsSorted.next()
                        continue
                    }
                    yield(currentLeft)
                    yieldAll(leftSummandsSorted)
                    break
                } else if (currentLeft == currentRight) {
                    if (rightSummandsSorted.hasNext()) {
                        currentRight = rightSummandsSorted.next()
                        continue
                    }
                    if (leftSummandsSorted.hasNext()) {
                        currentLeft = leftSummandsSorted.next()
                        continue
                    }
                    // both sequences are consumed aside from the last element
                    yield(currentLeft)
                    break
                }
            }
        }.iterator()
        val smallestElem = summandsSorted.next()
        if (!summandsSorted.hasNext()) {
            // possible when performing X u X, where X is not a Union
            return smallestElem
        }
        val secondSmallestElem = summandsSorted.next()
        // if there are two atomics (one from left and one from right), we need to merge them
        // they are the smallest elements so they will be first
        val leftmostElement = if (smallestElem is Regex.Atomic && secondSmallestElem is Regex.Atomic)
            Regex.Atomic(smallestElem.atomic + secondSmallestElem.atomic) else Regex.Union(
            smallestElem,
            secondSmallestElem
        )
        return summandsSorted.asSequence().fold(leftmostElement) { union, elem -> Regex.Union(union, elem) }
    }

    private fun <A : Comparable<A>> addLeftmostChildToConcat(parent: Regex.Concat<A>, child: Regex<A>): Regex.Concat<A> {
        val updatedLeftChild = if (parent.left is Regex.Concat) addLeftmostChildToConcat(parent.left, child)
        else Regex.Concat(child, parent.left)
        return Regex.Concat(updatedLeftChild, parent.right)
    }

    fun <A : Comparable<A>> createConcat(left: Regex<A>, right: Regex<A>): Regex<A> {
        // we maintain two invariants:
        // the right child of a Concat is never a Concat
        // Epsilon or Empty cannot be a part of Concat
        if (left is Regex.Empty || right is Regex.Empty) {
            return Regex.Empty()
        }
        if (left is Regex.Epsilon) {
            return right
        }
        if (right is Regex.Epsilon) {
            return left
        }
        if (right is Regex.Concat) {
            return addLeftmostChildToConcat(right, left)
        }
        return Regex.Concat(left, right)
    }
}
