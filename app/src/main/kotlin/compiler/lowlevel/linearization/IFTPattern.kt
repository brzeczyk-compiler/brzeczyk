package compiler.lowlevel.linearization

import compiler.intermediate.Constant
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

// Class that represents a pattern of an intermediate form tree
// Not to be confused with the Pattern interface
sealed class IFTPattern {

    data class MatchResult(val matchedSubtrees: List<IFTNode>, val matchedValues: Map<String, Any>)

    // Returns null if the pattern doesn't match provided intermediate form tree
    // If the tree matches, it returns the list of unmatched subtrees and
    // captured values (mainly used to store values in matched leaves)
    abstract fun match(node: IFTNode): MatchResult?

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
    data class ArgumentWhere<T>(val name: String? = null, private val predicate: (T) -> Boolean) : ArgumentPattern<T>() {
        override fun match(value: T): Map<String, Any>? {
            if (!predicate(value)) return null
            return if (name == null) emptyMap() else mapOf(name to value as Any)
        }
    }

    // Matches every intermediate form tree node
    // Doesn't consume the nodes, returns it as a subtree
    class AnyNode : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            return MatchResult(listOf(node), emptyMap())
        }
    }

    // Matches the first matching pattern in the provided list
    data class FirstOf(val possiblePatterns: List<IFTPattern>) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            possiblePatterns.forEach { pattern -> pattern.match(node)?.let { return it } }
            return null
        }
    }

    // Matches the first matching pattern in the provided list
    // Stores the name of the pattern under provided name
    data class FirstOfNamed(val name: String, val possiblePatterns: List<Pair<Any, IFTPattern>>) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            possiblePatterns.forEach { (patternName, pattern) ->
                pattern.match(node)?.let { return MatchResult(it.matchedSubtrees, it.matchedValues + mapOf(name to patternName)) }
            }
            return null
        }
    }

    data class BinaryOperator<T : IFTNode.BinaryOperator>(
        val type: KClass<T>,
        val leftPattern: IFTPattern = AnyNode(),
        val rightPattern: IFTPattern = AnyNode()
    ) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            val castedNode = type.safeCast(node) ?: return null
            val leftMatch = leftPattern.match(castedNode.left) ?: return null
            val rightMatch = rightPattern.match(castedNode.right) ?: return null
            return MatchResult(leftMatch.matchedSubtrees + rightMatch.matchedSubtrees, leftMatch.matchedValues + rightMatch.matchedValues)
        }
    }

    data class UnaryOperator<T : IFTNode.UnaryOperator>(
        val type: KClass<T>,
        val nodePattern: IFTPattern = AnyNode()
    ) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            val castedNode = type.safeCast(node) ?: return null
            return nodePattern.match(castedNode.node)
        }
    }

    data class MemoryWrite(
        val addressPattern: IFTPattern = AnyNode(),
        val valuePattern: IFTPattern = AnyNode()
    ) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            if (node !is IFTNode.MemoryWrite) return null
            val addressMatch = addressPattern.match(node.address) ?: return null
            val valueMatch = valuePattern.match(node.value) ?: return null
            return MatchResult(addressMatch.matchedSubtrees + valueMatch.matchedSubtrees, addressMatch.matchedValues + valueMatch.matchedValues)
        }
    }

    data class RegisterWrite(
        val registerPattern: ArgumentPattern<Register> = AnyArgument(),
        val nodePattern: IFTPattern = AnyNode()
    ) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            if (node !is IFTNode.RegisterWrite) return null
            val registerMatch = registerPattern.match(node.register) ?: return null
            val nodeMatch = nodePattern.match(node.node) ?: return null
            return MatchResult(nodeMatch.matchedSubtrees, registerMatch + nodeMatch.matchedValues)
        }
    }

    data class MemoryRead(val addressPattern: IFTPattern = AnyNode()) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            if (node !is IFTNode.MemoryRead) return null
            return addressPattern.match(node.address)
        }
    }

    data class RegisterRead(val registerPattern: ArgumentPattern<Register> = AnyArgument()) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            if (node !is IFTNode.RegisterRead) return null
            val registerMatch = registerPattern.match(node.register) ?: return null
            return MatchResult(emptyList(), registerMatch)
        }
    }

    data class MemoryLabel(val addressLabelPattern: ArgumentPattern<String> = AnyArgument()) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            if (node !is IFTNode.MemoryLabel) return null
            val addressLabelMatch = addressLabelPattern.match(node.label) ?: return null
            return MatchResult(emptyList(), addressLabelMatch)
        }
    }

    data class Const(val valuePattern: ArgumentPattern<Constant> = AnyArgument()) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            if (node !is IFTNode.Const) return null
            val valueMatch = valuePattern.match(node.value) ?: return null
            return MatchResult(emptyList(), valueMatch)
        }
    }

    data class StackPush(val nodePattern: IFTPattern = AnyNode()) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            if (node !is IFTNode.StackPush) return null
            return nodePattern.match(node.node)
        }
    }

    class StackPop : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            if (node !is IFTNode.StackPop) return null
            return MatchResult(emptyList(), emptyMap())
        }
    }

    data class Call(
        val addressPattern: IFTPattern = AnyNode(),
        val usedRegsPattern: ArgumentPattern<Collection<Register>> = AnyArgument(),
        val definedRegsPattern: ArgumentPattern<Collection<Register>> = AnyArgument()
    ) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            if (node !is IFTNode.Call) return null
            val addressMatch = addressPattern.match(node.address) ?: return null
            val usedRegsMatch = usedRegsPattern.match(node.usedRegisters) ?: return null
            val definedRegsMatch = definedRegsPattern.match(node.definedRegisters) ?: return null
            return MatchResult(addressMatch.matchedSubtrees, addressMatch.matchedValues + usedRegsMatch + definedRegsMatch)
        }
    }

    data class Return(val usedRegsPattern: ArgumentPattern<Collection<Register>> = AnyArgument()) : IFTPattern() {
        override fun match(node: IFTNode): MatchResult? {
            if (node !is IFTNode.Return) return null
            val usedRegsMatch = usedRegsPattern.match(node.usedRegisters) ?: return null
            return MatchResult(emptyList(), usedRegsMatch)
        }
    }
}
