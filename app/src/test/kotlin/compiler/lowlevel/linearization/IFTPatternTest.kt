package compiler.lowlevel.linearization

import compiler.intermediate.FixedConstant
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
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

        val patternLogNeg = IFTPattern.UnaryOperator(IFTNode.LogicalNegation::class)
        val patternLogIff = IFTPattern.BinaryOperator(IFTNode.LogicalIff::class)
        val patternLogXor = IFTPattern.BinaryOperator(IFTNode.LogicalXor::class)

        val patternNeg = IFTPattern.UnaryOperator(IFTNode.Negation::class)
        val patternAdd = IFTPattern.BinaryOperator(IFTNode.Add::class)
        val patternSub = IFTPattern.BinaryOperator(IFTNode.Subtract::class)
        val patternMul = IFTPattern.BinaryOperator(IFTNode.Multiply::class)
        val patternDiv = IFTPattern.BinaryOperator(IFTNode.Divide::class)
        val patternMod = IFTPattern.BinaryOperator(IFTNode.Modulo::class)

        val patternBitNeg = IFTPattern.UnaryOperator(IFTNode.BitNegation::class)
        val patternBitAnd = IFTPattern.BinaryOperator(IFTNode.BitAnd::class)
        val patternBitOr = IFTPattern.BinaryOperator(IFTNode.BitOr::class)
        val patternBitXor = IFTPattern.BinaryOperator(IFTNode.BitXor::class)
        val patternBitShiftLeft = IFTPattern.BinaryOperator(IFTNode.BitShiftLeft::class)
        val patternBitShiftRight = IFTPattern.BinaryOperator(IFTNode.BitShiftRight::class)

        val patternEquals = IFTPattern.BinaryOperator(IFTNode.Equals::class)
        val patternNotEquals = IFTPattern.BinaryOperator(IFTNode.NotEquals::class)
        val patternLessThan = IFTPattern.BinaryOperator(IFTNode.LessThan::class)
        val patternLessThanOrEquals = IFTPattern.BinaryOperator(IFTNode.LessThanOrEquals::class)
        val patternGreaterThan = IFTPattern.BinaryOperator(IFTNode.GreaterThan::class)
        val patternGreaterThanOrEquals = IFTPattern.BinaryOperator(IFTNode.GreaterThanOrEquals::class)

        val left = IFTNode.Const(0L)
        val right = IFTNode.Const(0L)
        val node = IFTNode.Const(0L)
        val reg = Register()

        val nodeMemRead = IFTNode.MemoryRead(node)
        val nodeMemAddress = IFTNode.MemoryLabel("address")
        val nodeRegRead = IFTNode.RegisterRead(reg)
        val nodeConst = IFTNode.Const(0L)
        val nodeMemWrite = IFTNode.MemoryWrite(left, right)
        val nodeRegWrite = IFTNode.RegisterWrite(reg, node)
        val nodeStackPush = IFTNode.StackPush(node)
        val nodeStackPop = IFTNode.StackPop()
        val nodeCall = IFTNode.Call(node, emptyList(), emptyList())

        val nodeLogNeg = IFTNode.LogicalNegation(node)
        val nodeLogIff = IFTNode.LogicalIff(left, right)
        val nodeLogXor = IFTNode.LogicalXor(left, right)

        val nodeNeg = IFTNode.Negation(node)
        val nodeAdd = IFTNode.Add(left, right)
        val nodeSub = IFTNode.Subtract(left, right)
        val nodeMul = IFTNode.Multiply(left, right)
        val nodeDiv = IFTNode.Divide(left, right)
        val nodeMod = IFTNode.Modulo(left, right)

        val nodeBitNeg = IFTNode.BitNegation(node)
        val nodeBitAnd = IFTNode.BitAnd(left, right)
        val nodeBitOr = IFTNode.BitOr(left, right)
        val nodeBitXor = IFTNode.BitXor(left, right)
        val nodeBitShiftLeft = IFTNode.BitShiftLeft(left, right)
        val nodeBitShiftRight = IFTNode.BitShiftRight(left, right)

        val nodeEquals = IFTNode.Equals(left, right)
        val nodeNotEquals = IFTNode.NotEquals(left, right)
        val nodeLessThan = IFTNode.LessThan(left, right)
        val nodeLessThanOrEquals = IFTNode.LessThanOrEquals(left, right)
        val nodeGreaterThan = IFTNode.GreaterThan(left, right)
        val nodeGreaterThanOrEquals = IFTNode.GreaterThanOrEquals(left, right)

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
            IFTNode.Add::class,
            IFTPattern.BinaryOperator(
                IFTNode.Multiply::class,
                IFTPattern.Const(IFTPattern.ArgumentIn(setOf(FixedConstant(1L), FixedConstant(2L), FixedConstant(4L), FixedConstant(8L)), "size")),
                IFTPattern.AnyNode()
            ),
            IFTPattern.AnyNode()
        )

        val leaf1 = IFTNode.Subtract(
            IFTNode.MemoryRead(IFTNode.MemoryLabel("asdf")),
            IFTNode.Const(1L)
        )
        val leaf2 = IFTNode.Add(
            IFTNode.Const(10L),
            IFTNode.RegisterRead(Register())
        )
        val node1 = IFTNode.Add(
            IFTNode.Multiply(
                IFTNode.Const(4L),
                leaf1
            ),
            leaf2
        )
        val node2 = IFTNode.Add(
            IFTNode.Multiply(
                IFTNode.Const(3L),
                IFTNode.RegisterRead(Register.RAX)
            ),
            IFTNode.Const(2L)
        )

        assertEquals(IFTPattern.MatchResult(listOf(leaf1, leaf2), mapOf("size" to FixedConstant(4L))), pattern.match(node1))
        assertEquals(null, pattern.match(node2))
    }

    @Test
    fun `test multi pattern patterns`() {
        val pattern1 = IFTPattern.FirstOf(
            listOf(
                IFTPattern.BinaryOperator(IFTNode.Add::class),
                IFTPattern.BinaryOperator(IFTNode.Subtract::class),
            )
        )
        val pattern2 = IFTPattern.FirstOfNamed(
            "operation",
            listOf(
                "add" to IFTPattern.BinaryOperator(IFTNode.Add::class),
                "subtract" to IFTPattern.BinaryOperator(IFTNode.Subtract::class),
            )
        )
        val pattern3 = IFTPattern.FirstOfNamed(
            "operation",
            listOf(
                "add" to IFTPattern.BinaryOperator(
                    IFTNode.Add::class,
                    IFTPattern.Const(IFTPattern.AnyArgument("a")),
                    IFTPattern.Const(IFTPattern.AnyArgument("b")),
                ),
                "subtract" to IFTPattern.BinaryOperator(
                    IFTNode.Subtract::class,
                    IFTPattern.Const(IFTPattern.AnyArgument("a")),
                    IFTPattern.Const(IFTPattern.AnyArgument("c")),
                )
            )
        )
        val left = IFTNode.Const(1L)
        val right = IFTNode.Const(2L)
        val node1 = IFTNode.Add(left, right)
        val node2 = IFTNode.Subtract(left, right)
        val node3 = IFTNode.Multiply(left, right)

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
