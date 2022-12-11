package compiler.intermediate_form

import kotlin.test.Test
import kotlin.test.assertEquals

internal class IFTPatternTest {
    @Test
    fun `test argument patterns`() {
        val pattern1 = IFTPattern.AnyArgument<Long>("arg")
        val pattern2 = IFTPattern.ArgumentIn(setOf(1L, 2L, 4L, 8L), "arg")
        val pattern3 = IFTPattern.AnyArgument<String>("arg")
        val pattern4 = IFTPattern.ArgumentIn(setOf(Register.RAX, Register.RDI), "reg")
        val pattern5 = IFTPattern.ArgumentWhere<Int>("val") { it < 0 }

        assertEquals(mapOf("arg" to 13L), pattern1.match(13L))
        assertEquals(mapOf("arg" to -2L), pattern1.match(-2L))

        assertEquals(null, pattern2.match(0L))
        assertEquals(mapOf("arg" to 1L), pattern2.match(1L))
        assertEquals(mapOf("arg" to 2L), pattern2.match(2L))
        assertEquals(null, pattern2.match(3L))
        assertEquals(mapOf("arg" to 4L), pattern2.match(4L))
        assertEquals(null, pattern2.match(13L))
        assertEquals(null, pattern2.match(-1L))

        assertEquals(mapOf("arg" to "asdf"), pattern3.match("asdf"))
        assertEquals(mapOf("arg" to "arg"), pattern3.match("arg"))

        assertEquals(mapOf("reg" to Register.RAX), pattern4.match(Register.RAX))
        assertEquals(mapOf("reg" to Register.RDI), pattern4.match(Register.RDI))
        assertEquals(null, pattern4.match(Register.R11))
        assertEquals(null, pattern4.match(Register.RDX))
        assertEquals(null, pattern4.match(Register()))

        assertEquals(mapOf("val" to -142), pattern5.match(-142))
        assertEquals(mapOf("val" to -16), pattern5.match(-16))
        assertEquals(mapOf("val" to -3), pattern5.match(-3))
        assertEquals(mapOf("val" to -1), pattern5.match(-1))
        assertEquals(null, pattern5.match(0))
        assertEquals(null, pattern5.match(1))
        assertEquals(null, pattern5.match(17))
        assertEquals(null, pattern5.match(1236))
    }

    @Test
    fun `test simple patterns`() {
        val patternMemRead = IFTPattern.MemoryRead()
        val patternMemAddress = IFTPattern.MemoryLabel(IFTPattern.AnyArgument("label"))
        val patternRegRead = IFTPattern.RegisterRead(IFTPattern.AnyArgument("reg"))
        val patternConst = IFTPattern.Const(IFTPattern.AnyArgument("val"))
        val patternMemWrite = IFTPattern.MemoryWrite()
        val patternRegWrite = IFTPattern.RegisterWrite(IFTPattern.AnyArgument("reg"))
        val patternStackPush = IFTPattern.StackPush()
        val patternStackPop = IFTPattern.StackPop()
        val patternCall = IFTPattern.Call()

        val patternLogNeg = IFTPattern.UnaryOperator(IntermediateFormTreeNode.LogicalNegation::class)
        val patternLogIff = IFTPattern.BinaryOperator(IntermediateFormTreeNode.LogicalIff::class)
        val patternLogXor = IFTPattern.BinaryOperator(IntermediateFormTreeNode.LogicalXor::class)

        val patternNeg = IFTPattern.UnaryOperator(IntermediateFormTreeNode.Negation::class)
        val patternAdd = IFTPattern.BinaryOperator(IntermediateFormTreeNode.Add::class)
        val patternSub = IFTPattern.BinaryOperator(IntermediateFormTreeNode.Subtract::class)
        val patternMul = IFTPattern.BinaryOperator(IntermediateFormTreeNode.Multiply::class)
        val patternDiv = IFTPattern.BinaryOperator(IntermediateFormTreeNode.Divide::class)
        val patternMod = IFTPattern.BinaryOperator(IntermediateFormTreeNode.Modulo::class)

        val patternBitNeg = IFTPattern.UnaryOperator(IntermediateFormTreeNode.BitNegation::class)
        val patternBitAnd = IFTPattern.BinaryOperator(IntermediateFormTreeNode.BitAnd::class)
        val patternBitOr = IFTPattern.BinaryOperator(IntermediateFormTreeNode.BitOr::class)
        val patternBitXor = IFTPattern.BinaryOperator(IntermediateFormTreeNode.BitXor::class)
        val patternBitShiftLeft = IFTPattern.BinaryOperator(IntermediateFormTreeNode.BitShiftLeft::class)
        val patternBitShiftRight = IFTPattern.BinaryOperator(IntermediateFormTreeNode.BitShiftRight::class)

        val patternEquals = IFTPattern.BinaryOperator(IntermediateFormTreeNode.Equals::class)
        val patternNotEquals = IFTPattern.BinaryOperator(IntermediateFormTreeNode.NotEquals::class)
        val patternLessThan = IFTPattern.BinaryOperator(IntermediateFormTreeNode.LessThan::class)
        val patternLessThanOrEquals = IFTPattern.BinaryOperator(IntermediateFormTreeNode.LessThanOrEquals::class)
        val patternGreaterThan = IFTPattern.BinaryOperator(IntermediateFormTreeNode.GreaterThan::class)
        val patternGreaterThanOrEquals = IFTPattern.BinaryOperator(IntermediateFormTreeNode.GreaterThanOrEquals::class)

        val left = IntermediateFormTreeNode.Const(0L)
        val right = IntermediateFormTreeNode.Const(0L)
        val node = IntermediateFormTreeNode.Const(0L)
        val reg = Register()

        val nodeMemRead = IntermediateFormTreeNode.MemoryRead(node)
        val nodeMemAddress = IntermediateFormTreeNode.MemoryLabel("address")
        val nodeRegRead = IntermediateFormTreeNode.RegisterRead(reg)
        val nodeConst = IntermediateFormTreeNode.Const(0L)
        val nodeMemWrite = IntermediateFormTreeNode.MemoryWrite(left, right)
        val nodeRegWrite = IntermediateFormTreeNode.RegisterWrite(reg, node)
        val nodeStackPush = IntermediateFormTreeNode.StackPush(node)
        val nodeStackPop = IntermediateFormTreeNode.StackPop()
        val nodeCall = IntermediateFormTreeNode.Call(node)

        val nodeLogNeg = IntermediateFormTreeNode.LogicalNegation(node)
        val nodeLogIff = IntermediateFormTreeNode.LogicalIff(left, right)
        val nodeLogXor = IntermediateFormTreeNode.LogicalXor(left, right)

        val nodeNeg = IntermediateFormTreeNode.Negation(node)
        val nodeAdd = IntermediateFormTreeNode.Add(left, right)
        val nodeSub = IntermediateFormTreeNode.Subtract(left, right)
        val nodeMul = IntermediateFormTreeNode.Multiply(left, right)
        val nodeDiv = IntermediateFormTreeNode.Divide(left, right)
        val nodeMod = IntermediateFormTreeNode.Modulo(left, right)

        val nodeBitNeg = IntermediateFormTreeNode.BitNegation(node)
        val nodeBitAnd = IntermediateFormTreeNode.BitAnd(left, right)
        val nodeBitOr = IntermediateFormTreeNode.BitOr(left, right)
        val nodeBitXor = IntermediateFormTreeNode.BitXor(left, right)
        val nodeBitShiftLeft = IntermediateFormTreeNode.BitShiftLeft(left, right)
        val nodeBitShiftRight = IntermediateFormTreeNode.BitShiftRight(left, right)

        val nodeEquals = IntermediateFormTreeNode.Equals(left, right)
        val nodeNotEquals = IntermediateFormTreeNode.NotEquals(left, right)
        val nodeLessThan = IntermediateFormTreeNode.LessThan(left, right)
        val nodeLessThanOrEquals = IntermediateFormTreeNode.LessThanOrEquals(left, right)
        val nodeGreaterThan = IntermediateFormTreeNode.GreaterThan(left, right)
        val nodeGreaterThanOrEquals = IntermediateFormTreeNode.GreaterThanOrEquals(left, right)

        assertEquals(Pair(listOf(node), emptyMap()), patternMemRead.match(nodeMemRead))
        assertEquals(Pair(emptyList(), mapOf("label" to "address")), patternMemAddress.match(nodeMemAddress))
        assertEquals(Pair(emptyList(), mapOf("reg" to reg)), patternRegRead.match(nodeRegRead))
        assertEquals(Pair(emptyList(), mapOf("val" to 0L)), patternConst.match(nodeConst))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternMemWrite.match(nodeMemWrite))
        assertEquals(Pair(listOf(node), mapOf("reg" to reg)), patternRegWrite.match(nodeRegWrite))
        assertEquals(Pair(listOf(node), emptyMap()), patternStackPush.match(nodeStackPush))
        assertEquals(Pair(emptyList(), emptyMap()), patternStackPop.match(nodeStackPop))
        assertEquals(Pair(listOf(node), emptyMap()), patternCall.match(nodeCall))

        assertEquals(Pair(listOf(node), emptyMap()), patternLogNeg.match(nodeLogNeg))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternLogIff.match(nodeLogIff))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternLogXor.match(nodeLogXor))

        assertEquals(Pair(listOf(node), emptyMap()), patternNeg.match(nodeNeg))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternAdd.match(nodeAdd))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternSub.match(nodeSub))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternMul.match(nodeMul))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternDiv.match(nodeDiv))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternMod.match(nodeMod))

        assertEquals(Pair(listOf(node), emptyMap()), patternBitNeg.match(nodeBitNeg))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternBitAnd.match(nodeBitAnd))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternBitOr.match(nodeBitOr))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternBitXor.match(nodeBitXor))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternBitShiftLeft.match(nodeBitShiftLeft))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternBitShiftRight.match(nodeBitShiftRight))

        assertEquals(Pair(listOf(left, right), emptyMap()), patternEquals.match(nodeEquals))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternNotEquals.match(nodeNotEquals))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternLessThan.match(nodeLessThan))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternLessThanOrEquals.match(nodeLessThanOrEquals))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternGreaterThan.match(nodeGreaterThan))
        assertEquals(Pair(listOf(left, right), emptyMap()), patternGreaterThanOrEquals.match(nodeGreaterThanOrEquals))
    }

    @Test
    fun `test expression pattern`() {
        val pattern = IFTPattern.BinaryOperator(
            IntermediateFormTreeNode.Add::class,
            IFTPattern.BinaryOperator(
                IntermediateFormTreeNode.Multiply::class,
                IFTPattern.Const(IFTPattern.ArgumentIn(setOf(1L, 2L, 4L, 8L), "size")),
                IFTPattern.AnyNode()
            ),
            IFTPattern.AnyNode()
        )

        val leaf1 = IntermediateFormTreeNode.Subtract(
            IntermediateFormTreeNode.MemoryRead(IntermediateFormTreeNode.MemoryLabel("asdf")),
            IntermediateFormTreeNode.Const(1L)
        )
        val leaf2 = IntermediateFormTreeNode.Add(
            IntermediateFormTreeNode.Const(10L),
            IntermediateFormTreeNode.RegisterRead(Register())
        )
        val node1 = IntermediateFormTreeNode.Add(
            IntermediateFormTreeNode.Multiply(
                IntermediateFormTreeNode.Const(4L),
                leaf1
            ),
            leaf2
        )
        val node2 = IntermediateFormTreeNode.Add(
            IntermediateFormTreeNode.Multiply(
                IntermediateFormTreeNode.Const(3L),
                IntermediateFormTreeNode.RegisterRead(Register.RAX)
            ),
            IntermediateFormTreeNode.Const(2L)
        )

        assertEquals(Pair(listOf(leaf1, leaf2), mapOf("size" to 4L)), pattern.match(node1))
        assertEquals(null, pattern.match(node2))
    }
}
