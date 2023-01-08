package compiler.intermediate.generators

import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Type
import compiler.ast.Variable
import compiler.intermediate.CFGLinkType
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.FixedConstant
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import compiler.utils.Ref
import compiler.utils.keyRefMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultFunctionDetailsGeneratorTest {
    private val functionLocation = IFTNode.MemoryLabel("address")

    private val resultDummyVariable = Variable(Variable.Kind.VALUE, "numberResult", Type.Number, null)
    private val resultVariableRegister = Register()

    private val callerSavedRegisters = listOf(Register.RAX, Register.RCX, Register.RDX, Register.RSI, Register.RDI, Register.R8, Register.R9, Register.R10, Register.R11)
    private val argumentPassingRegisters = listOf(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.R8, Register.R9)

    @Test
    fun `test genCall for zero argument, Unit function`() {
        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            functionLocation,
            0u,
            mapOf(),
            IFTNode.Const(0)
        )
        val expected = ControlFlowGraphBuilder(IFTNode.Call(functionLocation, emptyList(), callerSavedRegisters)).build()
        val result = fdg.genCall(listOf())

        assert(expected.isIsomorphicTo(result.callGraph))
        assertEquals(null, result.result)
        assertEquals(null, result.secondResult)
    }

    @Test
    fun `test genCall for two argument, Unit return function`() {
        val param1 = Function.Parameter("x", Type.Number, null)
        val param2 = Function.Parameter("y", Type.Boolean, null)
        val fdg = DefaultFunctionDetailsGenerator(
            listOf(param1, param2),
            null,
            functionLocation,
            0u,
            keyRefMapOf(param1 to VariableLocationType.REGISTER, param2 to VariableLocationType.MEMORY),
            IFTNode.Const(0)
        )

        val arg1 = IFTNode.Dummy()
        val arg2 = IFTNode.Dummy()
        val result = fdg.genCall(listOf(arg1, arg2))

        val expectedCFGBuilder = ControlFlowGraphBuilder()
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.RegisterWrite(Register.RDI, arg1)
        )
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.RegisterWrite(Register.RSI, arg2)
        )
        expectedCFGBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, IFTNode.Call(functionLocation, argumentPassingRegisters.take(2), callerSavedRegisters))
        val expected = expectedCFGBuilder.build()

        assert(expected.isIsomorphicTo(result.callGraph))
        assertEquals(null, result.result)
        assertEquals(null, result.secondResult)
    }

    @Test
    fun `test genCall for zero argument, Number returning function`() {
        val variableToRegisterMap = mapOf(resultDummyVariable to resultVariableRegister)
        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            resultDummyVariable,
            functionLocation,
            0u,
            keyRefMapOf(resultDummyVariable to VariableLocationType.REGISTER),
            IFTNode.Const(0)
        ) { variableToRegisterMap[it]!! }

        val expectedCFGBuilder = ControlFlowGraphBuilder(IFTNode.Call(functionLocation, emptyList(), callerSavedRegisters))

        val expectedResult = IFTNode.RegisterRead(Register.RAX)
        val expected = expectedCFGBuilder.build()
        val result = fdg.genCall(listOf())
        assert(expected.isIsomorphicTo(result.callGraph))
        assertEquals(expectedResult, result.result)
        assertEquals(null, result.secondResult)
    }

    @Test
    fun `test genCall for 8 argument, Number return function`() {
        val params: List<NamedNode> = (0..7).map { Function.Parameter(it.toString(), Type.Number, null) }.toList()
        val variableToRegisterMap = params.associateWith { Register() } + mapOf(resultDummyVariable to resultVariableRegister)
        val variablesLocation = params.associate { Ref(it) to VariableLocationType.REGISTER } + keyRefMapOf(resultDummyVariable to VariableLocationType.REGISTER)

        val fdg = DefaultFunctionDetailsGenerator(
            params,
            resultDummyVariable,
            functionLocation,
            0u,
            variablesLocation,
            IFTNode.Const(0)
        ) { variableToRegisterMap[it]!! }

        val args = (0..7).map { IFTNode.Const(it.toLong()) }.toList()
        val result = fdg.genCall(args)

        val expectedCFGBuilder = ControlFlowGraphBuilder()

        // write args
        for ((argRegister, arg) in argumentPassingRegisters zip args) {
            expectedCFGBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(argRegister, arg)
            )
        }
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.StackPush(args[7])
        )
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.StackPush(args[6])
        )
        // call
        val callNode = IFTNode.Call(functionLocation, argumentPassingRegisters, callerSavedRegisters)
        expectedCFGBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, callNode)

        // remove arguments previously put on stack
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.RegisterWrite(
                Register.RSP,
                IFTNode.Add(
                    IFTNode.RegisterRead(Register.RSP),
                    IFTNode.Const(16)
                )
            )
        )

        val expectedResult = IFTNode.RegisterRead(Register.RAX)
        val expected = expectedCFGBuilder.build()

        assert(expected.isIsomorphicTo(result.callGraph))
        assertEquals(expectedResult, result.result)
        assertEquals(null, result.secondResult)
    }

    @Test
    fun `test genCall ensures that stack is aligned`() {
        val params: List<NamedNode> = (0..8).map { Function.Parameter(it.toString(), Type.Number, null) }.toList()
        val variableToRegisterMap = params.associateWith { Register() } + mapOf(resultDummyVariable to resultVariableRegister)
        val variablesLocation = params.associate { Ref(it) to VariableLocationType.REGISTER } + keyRefMapOf(resultDummyVariable to VariableLocationType.REGISTER)

        val fdg = DefaultFunctionDetailsGenerator(
            params,
            null,
            functionLocation,
            0u,
            variablesLocation,
            IFTNode.Const(0)
        ) { variableToRegisterMap[it]!! }

        val args = (0..8).map { IFTNode.Const(it.toLong()) }.toList()
        val result = fdg.genCall(args)

        val expectedCFGBuilder = ControlFlowGraphBuilder()

        // align stack
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.RegisterWrite(
                Register.RSP,
                IFTNode.Subtract(
                    IFTNode.RegisterRead(Register.RSP),
                    IFTNode.Const(8)
                )
            )
        )

        // write first 6 args to registers
        for ((argRegister, arg) in argumentPassingRegisters zip args) {
            expectedCFGBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(argRegister, arg)
            )
        }

        // write stack args
        for (stackArgPos in 8 downTo 6) {
            expectedCFGBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.StackPush(args[stackArgPos])
            )
        }

        // call
        val callNode = IFTNode.Call(functionLocation, argumentPassingRegisters, callerSavedRegisters)
        expectedCFGBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, callNode)

        // remove arguments previously put on stack
        expectedCFGBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.RegisterWrite(
                Register.RSP,
                IFTNode.Add(
                    IFTNode.RegisterRead(Register.RSP),
                    IFTNode.Const(8 * 4) // 3 args + stack alignment
                )
            )
        )
        val expected = expectedCFGBuilder.build()
        assert(expected.isIsomorphicTo(result.callGraph))
    }

    @Test
    fun `test gen read direct from memory`() {
        val memVar = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IFTNode.MemoryLabel(""),
            0u,
            keyRefMapOf(Pair(memVar, VariableLocationType.MEMORY)),
            IFTNode.Const(0)
        )

        var readMemVar = fdg.genRead(memVar, true)

        assertTrue { readMemVar is IFTNode.MemoryRead }

        readMemVar = readMemVar as IFTNode.MemoryRead
        assertTrue { readMemVar.address is IFTNode.Subtract }

        val left = (readMemVar.address as IFTNode.Subtract).left
        val right = (readMemVar.address as IFTNode.Subtract).right

        assertTrue { left is IFTNode.RegisterRead }
        assertEquals(Register.RBP, (left as IFTNode.RegisterRead).register)

        assertTrue { right is IFTNode.Const }
        assertEquals(FixedConstant(memoryUnitSize.toLong()), (right as IFTNode.Const).value)
    }

    @Test
    fun `test gen read indirect from memory`() {
        val memVar = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)
        val displayAddress = IFTNode.MemoryLabel("display")
        val depth: ULong = 5u
        val displayElementAddress = IFTNode.Add(
            displayAddress,
            IFTNode.Const((memoryUnitSize * depth).toLong())
        )

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IFTNode.MemoryLabel(""),
            depth,
            keyRefMapOf(Pair(memVar, VariableLocationType.MEMORY)),
            displayAddress
        )

        var readMemVar = fdg.genRead(memVar, false)

        assertTrue { readMemVar is IFTNode.MemoryRead }

        readMemVar = readMemVar as IFTNode.MemoryRead
        assertTrue { readMemVar.address is IFTNode.Subtract }

        val left = (readMemVar.address as IFTNode.Subtract).left
        val right = (readMemVar.address as IFTNode.Subtract).right

        assertEquals(IFTNode.MemoryRead(displayElementAddress), left)

        assertTrue { right is IFTNode.Const }
        assertEquals(FixedConstant(memoryUnitSize.toLong()), (right as IFTNode.Const).value)
    }

    @Test
    fun `test gen read direct from register`() {
        val regVar = Variable(Variable.Kind.VALUE, "regVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IFTNode.MemoryLabel(""),
            0u,
            keyRefMapOf(Pair(regVar, VariableLocationType.REGISTER)),
            IFTNode.Const(0)
        )

        val readRegVar = fdg.genRead(regVar, true)

        assertTrue { readRegVar is IFTNode.RegisterRead }
    }

    @Test
    fun `test gen read indirect from register fails`() {
        val regVar: Variable = Variable(Variable.Kind.VALUE, "regVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IFTNode.MemoryLabel(""),
            0u,
            keyRefMapOf(Pair(regVar, VariableLocationType.REGISTER)),
            IFTNode.Const(0)
        )

        assertFailsWith<DefaultFunctionDetailsGenerator.IndirectRegisterAccess> { fdg.genRead(regVar, false) }
    }

    @Test
    fun `test gen write direct to memory`() {
        val memVar: Variable = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IFTNode.MemoryLabel(""),
            0u,
            keyRefMapOf(Pair(memVar, VariableLocationType.MEMORY)),
            IFTNode.Const(0)
        )

        val value = IFTNode.Const(1)
        var writeMemVar = fdg.genWrite(memVar, value, true)

        assertTrue { writeMemVar is IFTNode.MemoryWrite }

        writeMemVar = writeMemVar as IFTNode.MemoryWrite
        assertTrue { writeMemVar.address is IFTNode.Subtract }
        assertTrue { writeMemVar.value == value }

        val left = (writeMemVar.address as IFTNode.Subtract).left
        val right = (writeMemVar.address as IFTNode.Subtract).right

        assertTrue { left is IFTNode.RegisterRead }
        assertEquals(Register.RBP, (left as IFTNode.RegisterRead).register)

        assertTrue { right is IFTNode.Const }
        assertEquals(FixedConstant(memoryUnitSize.toLong()), (right as IFTNode.Const).value)
    }

    @Test
    fun `test gen write indirect to memory`() {
        val memVar: Variable = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)
        val displayAddress = IFTNode.MemoryLabel("display")
        val depth: ULong = 5u
        val displayElementAddress = IFTNode.Add(
            displayAddress,
            IFTNode.Const((memoryUnitSize * depth).toLong())
        )

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IFTNode.MemoryLabel(""),
            depth,
            keyRefMapOf(Pair(memVar, VariableLocationType.MEMORY)),
            displayAddress
        )

        val value = IFTNode.Const(1)
        var writeMemVar = fdg.genWrite(memVar, value, false)

        assertTrue { writeMemVar is IFTNode.MemoryWrite }

        writeMemVar = writeMemVar as IFTNode.MemoryWrite
        assertTrue { writeMemVar.address is IFTNode.Subtract }
        assertTrue { writeMemVar.value == value }

        val left = (writeMemVar.address as IFTNode.Subtract).left
        val right = (writeMemVar.address as IFTNode.Subtract).right

        assertEquals(IFTNode.MemoryRead(displayElementAddress), left)

        assertTrue { right is IFTNode.Const }
        assertEquals(FixedConstant(memoryUnitSize.toLong()), (right as IFTNode.Const).value)
    }

    @Test
    fun `test gen write direct to register`() {
        val regVar: Variable = Variable(Variable.Kind.VALUE, "regVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IFTNode.MemoryLabel(""),
            0u,
            keyRefMapOf(Pair(regVar, VariableLocationType.REGISTER)),
            IFTNode.Const(0)
        )

        val value = IFTNode.Const(1)
        val writeRegVal = fdg.genWrite(regVar, value, true)

        assertTrue { writeRegVal is IFTNode.RegisterWrite }
        assertTrue { (writeRegVal as IFTNode.RegisterWrite).node == value }
    }

    @Test
    fun `test gen write indirect to register fails`() {
        val regVar: Variable = Variable(Variable.Kind.VALUE, "regVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IFTNode.MemoryLabel(""),
            0u,
            keyRefMapOf(Pair(regVar, VariableLocationType.REGISTER)),
            IFTNode.Const(0)
        )

        val value = IFTNode.Const(1)
        assertFailsWith<DefaultFunctionDetailsGenerator.IndirectRegisterAccess> { fdg.genWrite(regVar, value, false) }
    }
}
