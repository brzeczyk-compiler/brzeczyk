package compiler.intermediate_form

import kotlin.reflect.KClass
import kotlin.reflect.safeCast

sealed class Pattern {

    // Returns null if the pattern doesn't match provided intermediate form tree
    // If the tree matches, it returns the list of unmatched subtrees and
    // captured values (mainly used to store values in matched leaves)
    // TODO: remove this one and replace with the following ones.
    // TODO (optional) I also suggest to change this very convoluted type: Pair<List<IntermediateFormTreeNode>, Map<String, Any>> to a data class with named fields.
    abstract fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>?

    fun matchValue(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? = null
    fun matchUnconditional(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? = null
    fun matchConditional(node: IntermediateFormTreeNode, targetLabel: String, invert: Boolean): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? = null

    // This class is used to match arguments in nodes which aren't other nodes
    // It's used when we want to create instructions for specific values of arguments (constants, labels or registers)
    sealed class ArgumentPattern<T> {
        // Return null if the argument doesn't match
        // If it matches it returns a possibly empty map, which can contain information about the argument
        // In all current cases it returns a mapping from a provided name to the value of the argument if the name is not null
        abstract fun match(value: T): Map<String, Any>?
    }

    // Matches any value of type T and maps it to the provided name
    data class AnyArgument<T>(val name: String? = null) : ArgumentPattern<T>() {
        override fun match(value: T): Map<String, Any> {
            return if (name == null) emptyMap() else mapOf(name to value as Any)
        }
    }

    // Matches any value in allowed set and maps it to the provided name
    data class ArgumentIn<T>(private val allowed: Set<T>, val name: String? = null) : ArgumentPattern<T>() {
        override fun match(value: T): Map<String, Any>? {
            if (!allowed.contains(value)) return null
            return if (name == null) emptyMap() else mapOf(name to value as Any)
        }
    }

    // Matches any value that fulfill a provided predicate and maps it to the provided name
    data class ArgumentWhere<T>(val name: String? = null, val predicate: (T) -> Boolean) : ArgumentPattern<T>() {
        override fun match(value: T): Map<String, Any>? {
            if (!predicate(value)) return null
            return if (name == null) emptyMap() else mapOf(name to value as Any)
        }
    }

    // Matches every intermediate form tree node
    class AnyNode : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            return Pair(listOf(node), emptyMap())
        }
    }

    // Matches the first matching pattern in the provided list
    // Stores the name of the pattern under name
    data class FirstOf(val name: String, val possiblePatterns: List<Pair<Any, Pattern>>) : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            for ((patternName, pattern) in possiblePatterns) {
                val match = pattern.match(node)
                if (match != null) {
                    return Pair(match.first, match.second + mapOf(name to patternName))
                }
            }
            return null
        }
    }

    data class BinaryOperator<T : IntermediateFormTreeNode.BinaryOperator>(
        val type: KClass<T>,
        val leftPattern: Pattern = AnyNode(),
        val rightPattern: Pattern = AnyNode()
    ) : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            val castedNode = type.safeCast(node) ?: return null
            val leftMatch = leftPattern.match(castedNode.left) ?: return null
            val rightMatch = rightPattern.match(castedNode.right) ?: return null
            return Pair(leftMatch.first + rightMatch.first, leftMatch.second + rightMatch.second)
        }
    }

    data class UnaryOperator<T : IntermediateFormTreeNode.UnaryOperator>(
        val type: KClass<T>,
        val nodePattern: Pattern = AnyNode()
    ) : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            val castedNode = type.safeCast(node) ?: return null
            return nodePattern.match(castedNode.node)
        }
    }

    data class MemoryWrite(
        val addressPattern: Pattern = AnyNode(),
        val valuePattern: Pattern = AnyNode()
    ) : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            if (node !is IntermediateFormTreeNode.MemoryWrite) return null
            val addressMatch = addressPattern.match(node.address) ?: return null
            val valueMatch = valuePattern.match(node.value) ?: return null
            return Pair(addressMatch.first + valueMatch.first, addressMatch.second + valueMatch.second)
        }
    }

    data class RegisterWrite(
        val registerPattern: ArgumentPattern<Register> = AnyArgument(),
        val nodePattern: Pattern = AnyNode()
    ) : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            if (node !is IntermediateFormTreeNode.RegisterWrite) return null
            val registerMatch = registerPattern.match(node.register) ?: return null
            val nodeMatch = nodePattern.match(node.node) ?: return null
            return Pair(nodeMatch.first, registerMatch + nodeMatch.second)
        }
    }

    data class MemoryRead(val addressPattern: Pattern = AnyNode()) : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            if (node !is IntermediateFormTreeNode.MemoryRead) return null
            return addressPattern.match(node.address)
        }
    }

    data class RegisterRead(val registerPattern: ArgumentPattern<Register> = AnyArgument()) : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            if (node !is IntermediateFormTreeNode.RegisterRead) return null
            val registerMatch = registerPattern.match(node.register) ?: return null
            return Pair(emptyList(), registerMatch)
        }
    }

    data class MemoryLabel(val addressLabelPattern: ArgumentPattern<String> = AnyArgument()) : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            if (node !is IntermediateFormTreeNode.MemoryLabel) return null
            val match = addressLabelPattern.match(node.label) ?: return null
            return Pair(emptyList(), match)
        }
    }

    data class Const(val valuePattern: ArgumentPattern<Long> = AnyArgument()) : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            if (node !is IntermediateFormTreeNode.Const) return null
            val match = valuePattern.match(node.value) ?: return null
            return Pair(emptyList(), match)
        }
    }

    data class StackPush(val nodePattern: Pattern = AnyNode()) : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            if (node !is IntermediateFormTreeNode.StackPush) return null
            return nodePattern.match(node.node)
        }
    }

    class StackPop : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            if (node !is IntermediateFormTreeNode.StackPop) return null
            return Pair(emptyList(), emptyMap())
        }
    }

    data class Call(val addressPattern: Pattern = AnyNode()) : Pattern() {
        override fun match(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>? {
            if (node !is IntermediateFormTreeNode.Call) return null
            val addressMatch = addressPattern.match(node.address) ?: return null
            return Pair(addressMatch.first, addressMatch.second)
        }
    }
}
