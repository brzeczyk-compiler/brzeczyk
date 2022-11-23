package compiler.intermediate_form

sealed class IntermediateFormTreeNode {
    data class MemoryRead(val address: Addressing) : IntermediateFormTreeNode()
    data class RegisterRead(val register: Register) : IntermediateFormTreeNode()
    data class Const(val value: Long) : IntermediateFormTreeNode()

    data class MemoryWrite(val address: MemoryAddress, val node: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class RegisterWrite(val register: Register, val node: IntermediateFormTreeNode) : IntermediateFormTreeNode()

    // no logical and, or, since they have a short circuit semantic
    data class LogicalNegation(val node: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class LogicalIff(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class LogicalXor(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()

    data class Add(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class Subtract(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class Multiply(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class Divide(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class Modulo(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()

    data class BitAnd(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class BitOr(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class BitXor(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class BitShiftLeft(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class BitShiftRight(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class BitNegation(val node: IntermediateFormTreeNode) : IntermediateFormTreeNode()

    data class Equals(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class NotEquals(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class LessThan(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class LessThanOrEquals(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class GreaterThan(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class GreaterThanOrEquals(val left: IntermediateFormTreeNode, val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()

    data class StackPush(val Node: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    class StackPop : IntermediateFormTreeNode()

    class NoOp : IntermediateFormTreeNode() // For testing
}
