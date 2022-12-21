package compiler.intermediate

import compiler.ast.Function
import compiler.ast.NamedNode

sealed class IFTNode {
    companion object {
        const val UNIT_VALUE: Long = 0
    }

    data class MemoryRead(val address: IFTNode) : IFTNode()
    data class MemoryLabel(val label: String) : IFTNode()
    data class RegisterRead(val register: Register) : IFTNode()

    data class Const(val value: Constant) : IFTNode() {
        constructor(value: Long) : this(FixedConstant(value))
    }

    data class MemoryWrite(val address: IFTNode, val value: IFTNode) : IFTNode()
    data class RegisterWrite(val register: Register, val node: IFTNode) : IFTNode()

    sealed class BinaryOperator(open val left: IFTNode, open val right: IFTNode) : IFTNode()
    sealed class UnaryOperator(open val node: IFTNode) : IFTNode()

    // no logical and, or, since they have a short circuit semantic
    data class LogicalNegation(override val node: IFTNode) : UnaryOperator(node)
    data class LogicalIff(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class LogicalXor(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)

    data class Negation(override val node: IFTNode) : UnaryOperator(node)
    data class Add(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class Subtract(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class Multiply(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class Divide(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class Modulo(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)

    data class BitAnd(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class BitOr(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class BitXor(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class BitShiftLeft(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class BitShiftRight(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class BitNegation(override val node: IFTNode) : UnaryOperator(node)

    data class Equals(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class NotEquals(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class LessThan(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class LessThanOrEquals(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class GreaterThan(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)
    data class GreaterThanOrEquals(override val left: IFTNode, override val right: IFTNode) : BinaryOperator(left, right)

    data class StackPush(val node: IFTNode) : IFTNode()
    class StackPop : IFTNode()

    data class Call(val address: IFTNode) : IFTNode()
    // return node is implicit when there's no link from a tree root

    // test nodes
    class NoOp : IFTNode()
    data class DummyRead(val namedNode: NamedNode, val isDirect: Boolean, val isGlobal: Boolean = false) : IFTNode()
    data class DummyWrite(val namedNode: NamedNode, val value: IFTNode, val isDirect: Boolean, val isGlobal: Boolean = false) : IFTNode()
    class DummyCallResult : IFTNode()
    data class DummyCall(val function: Function, val args: List<IFTNode>, val callResult: DummyCallResult) : IFTNode()
}