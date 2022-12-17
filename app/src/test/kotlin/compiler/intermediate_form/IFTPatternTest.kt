package compiler.intermediate_form

import compiler.common.FixedConstant
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

        assertEquals(IFTPattern.MatchResult(listOf(node), emptyMap()), patternMemRead.match(nodeMemRead))
        assertEquals(IFTPattern.MatchResult(emptyList(), mapOf("label" to "address")), patternMemAddress.match(nodeMemAddress))
        assertEquals(IFTPattern.MatchResult(emptyList(), mapOf("reg" to reg)), patternRegRead.match(nodeRegRead))
        assertEquals(IFTPattern.MatchResult(emptyList(), mapOf("val" to FixedConstant(0L))), patternConst.match(nodeConst))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternMemWrite.match(nodeMemWrite))
        assertEquals(IFTPattern.MatchResult(listOf(node), mapOf("reg" to reg)), patternRegWrite.match(nodeRegWrite))
        assertEquals(IFTPattern.MatchResult(listOf(node), emptyMap()), patternStackPush.match(nodeStackPush))
        assertEquals(IFTPattern.MatchResult(emptyList(), emptyMap()), patternStackPop.match(nodeStackPop))
        assertEquals(IFTPattern.MatchResult(listOf(node), emptyMap()), patternCall.match(nodeCall))

        assertEquals(IFTPattern.MatchResult(listOf(node), emptyMap()), patternLogNeg.match(nodeLogNeg))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternLogIff.match(nodeLogIff))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternLogXor.match(nodeLogXor))

        assertEquals(IFTPattern.MatchResult(listOf(node), emptyMap()), patternNeg.match(nodeNeg))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternAdd.match(nodeAdd))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternSub.match(nodeSub))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternMul.match(nodeMul))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternDiv.match(nodeDiv))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternMod.match(nodeMod))

        assertEquals(IFTPattern.MatchResult(listOf(node), emptyMap()), patternBitNeg.match(nodeBitNeg))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternBitAnd.match(nodeBitAnd))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternBitOr.match(nodeBitOr))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternBitXor.match(nodeBitXor))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternBitShiftLeft.match(nodeBitShiftLeft))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternBitShiftRight.match(nodeBitShiftRight))

        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternEquals.match(nodeEquals))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternNotEquals.match(nodeNotEquals))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternLessThan.match(nodeLessThan))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternLessThanOrEquals.match(nodeLessThanOrEquals))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternGreaterThan.match(nodeGreaterThan))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), patternGreaterThanOrEquals.match(nodeGreaterThanOrEquals))
    }

    @Test
    fun `test expression pattern`() {
        val pattern = IFTPattern.BinaryOperator(
            IntermediateFormTreeNode.Add::class,
            IFTPattern.BinaryOperator(
                IntermediateFormTreeNode.Multiply::class,
                IFTPattern.Const(IFTPattern.ArgumentIn(setOf(FixedConstant(1L), FixedConstant(2L), FixedConstant(4L), FixedConstant(8L)), "size")),
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

        assertEquals(IFTPattern.MatchResult(listOf(leaf1, leaf2), mapOf("size" to FixedConstant(4L))), pattern.match(node1))
        assertEquals(null, pattern.match(node2))
    }

    @Test
    fun `test multi pattern patterns`() {
        val pattern1 = IFTPattern.FirstOf(
            listOf(
                IFTPattern.BinaryOperator(IntermediateFormTreeNode.Add::class),
                IFTPattern.BinaryOperator(IntermediateFormTreeNode.Subtract::class),
            )
        )
        val pattern2 = IFTPattern.FirstOfNamed(
            "operation",
            listOf(
                "add" to IFTPattern.BinaryOperator(IntermediateFormTreeNode.Add::class),
                "subtract" to IFTPattern.BinaryOperator(IntermediateFormTreeNode.Subtract::class),
            )
        )
        val pattern3 = IFTPattern.FirstOfNamed(
            "operation",
            listOf(
                "add" to IFTPattern.BinaryOperator(
                    IntermediateFormTreeNode.Add::class,
                    IFTPattern.Const(IFTPattern.AnyArgument("a")),
                    IFTPattern.Const(IFTPattern.AnyArgument("b")),
                ),
                "subtract" to IFTPattern.BinaryOperator(
                    IntermediateFormTreeNode.Subtract::class,
                    IFTPattern.Const(IFTPattern.AnyArgument("a")),
                    IFTPattern.Const(IFTPattern.AnyArgument("c")),
                )
            )
        )
        val left = IntermediateFormTreeNode.Const(1L)
        val right = IntermediateFormTreeNode.Const(2L)
        val node1 = IntermediateFormTreeNode.Add(left, right)
        val node2 = IntermediateFormTreeNode.Subtract(left, right)
        val node3 = IntermediateFormTreeNode.Multiply(left, right)

        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), pattern1.match(node1))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), emptyMap()), pattern1.match(node2))
        assertEquals(null, pattern1.match(node3))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), mapOf("operation" to "add")), pattern2.match(node1))
        assertEquals(IFTPattern.MatchResult(listOf(left, right), mapOf("operation" to "subtract")), pattern2.match(node2))
        assertEquals(null, pattern2.match(node3))
        assertEquals(
            IFTPattern.MatchResult(emptyList(), mapOf("operation" to "add", "a" to FixedConstant(1L), "b" to FixedConstant(2L))),
            pattern3.match(node1)
        )
        assertEquals(
            IFTPattern.MatchResult(emptyList(), mapOf("operation" to "subtract", "a" to FixedConstant(1L), "c" to FixedConstant(2L))),
            pattern3.match(node2)
        )
        assertEquals(null, pattern2.match(node3))
    }
}
