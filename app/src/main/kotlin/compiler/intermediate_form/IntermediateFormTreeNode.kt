package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.NamedNode

sealed class IntermediateFormTreeNode {
    companion object {
        const val UNIT_VALUE: Long = 0
    }

    data class MemoryRead(val address: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class MemoryLabel(val label: String) : IntermediateFormTreeNode()
    data class RegisterRead(val register: Register) : IntermediateFormTreeNode()
    data class Const(val value: Long) : IntermediateFormTreeNode()

    data class MemoryWrite(val address: IntermediateFormTreeNode, val value: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    data class RegisterWrite(val register: Register, val node: IntermediateFormTreeNode) : IntermediateFormTreeNode()

    sealed class BinaryOperator(open val left: IntermediateFormTreeNode, open val right: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    sealed class UnaryOperator(open val node: IntermediateFormTreeNode) : IntermediateFormTreeNode()

    // no logical and, or, since they have a short circuit semantic
    data class LogicalNegation(override val node: IntermediateFormTreeNode) : UnaryOperator(node)
    data class LogicalIff(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class LogicalXor(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)

    data class Negation(override val node: IntermediateFormTreeNode) : UnaryOperator(node)
    data class Add(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class Subtract(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class Multiply(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class Divide(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class Modulo(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)

    data class BitAnd(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class BitOr(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class BitXor(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class BitShiftLeft(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class BitShiftRight(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class BitNegation(override val node: IntermediateFormTreeNode) : UnaryOperator(node)

    data class Equals(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class NotEquals(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class LessThan(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class LessThanOrEquals(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class GreaterThan(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)
    data class GreaterThanOrEquals(override val left: IntermediateFormTreeNode, override val right: IntermediateFormTreeNode) : BinaryOperator(left, right)

    data class StackPush(val node: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    class StackPop : IntermediateFormTreeNode()

    data class Call(val address: IntermediateFormTreeNode) : IntermediateFormTreeNode()
    class Return() : IntermediateFormTreeNode()

    // test nodes
    class NoOp : IntermediateFormTreeNode()
    data class DummyRead(val namedNode: NamedNode, val isDirect: Boolean, val isGlobal: Boolean = false) : IntermediateFormTreeNode()
    data class DummyWrite(val namedNode: NamedNode, val value: IntermediateFormTreeNode, val isDirect: Boolean, val isGlobal: Boolean = false) : IntermediateFormTreeNode()
    class DummyCallResult : IntermediateFormTreeNode()
    data class DummyCall(val function: Function, val args: List<IntermediateFormTreeNode>, val callResult: DummyCallResult) : IntermediateFormTreeNode()
}
