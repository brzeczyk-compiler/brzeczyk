package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.Type
import compiler.ast.Variable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultDefaultFunctionDetailsGeneratorTest {
    private val functionLocation = IntermediateFormTreeNode.MemoryLabel("address")

    private val resultDummyVariable = Variable(Variable.Kind.VALUE, "numberResult", Type.Number, null)
    private val resultVariableRegister = Register()

    @Test
    fun `test genCall for zero argument, Unit function`() {
        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            functionLocation,
            0u,
            mapOf(),
            IntermediateFormTreeNode.Const(0)
        )
        val expected = ControlFlowGraphBuilder(IntermediateFormTreeNode.Call(functionLocation)).build()
        val result = fdg.genCall(listOf())

        assert(expected.equalsByValue(result.callGraph))
        assertEquals(null, result.result)
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
            mapOf(param1 to VariableLocationType.REGISTER, param2 to VariableLocationType.MEMORY),
            IntermediateFormTreeNode.Const(0)
        )

        val arg1 = IntermediateFormTreeNode.NoOp()
        val arg2 = IntermediateFormTreeNode.NoOp()
        val result = fdg.genCall(listOf(arg1, arg2))

        val expectedCFGBuilder = ControlFlowGraphBuilder()
        expectedCFGBuilder.addLinkFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.RegisterWrite(Register.RDI, arg1)
        )
        expectedCFGBuilder.addLinkFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.RegisterWrite(Register.RSI, arg2)
        )
        expectedCFGBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, IntermediateFormTreeNode.Call(functionLocation))
        val expected = expectedCFGBuilder.build()

        assert(expected.equalsByValue(result.callGraph))
        assertEquals(null, result.result)
    }

    @Test
    fun `test genCall for zero argument, Number returning function`() {
        val variableToRegisterMap = mapOf(resultDummyVariable to resultVariableRegister)
        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            resultDummyVariable,
            functionLocation,
            0u,
            mapOf(resultDummyVariable to VariableLocationType.REGISTER),
            IntermediateFormTreeNode.Const(0)
        ) { variableToRegisterMap[it]!! }

        val expectedCFGBuilder = ControlFlowGraphBuilder(IntermediateFormTreeNode.Call(functionLocation))

        val expectedResult = IntermediateFormTreeNode.RegisterRead(Register.RAX)
        val expected = expectedCFGBuilder.build()
        val result = fdg.genCall(listOf())
        assert(expected.equalsByValue(result.callGraph))
        assertEquals(expectedResult, result.result)
    }

    @Test
    fun `test genCall for 8 argument, Number return function`() {
        val params = (0..7).map { Function.Parameter(it.toString(), Type.Number, null) }.toList()
        val variableToRegisterMap = params.associateWith { Register() } + mapOf(resultDummyVariable to resultVariableRegister)
        val variablesLocation = params.associateWith { VariableLocationType.REGISTER } + mapOf(resultDummyVariable to VariableLocationType.REGISTER)

        val fdg = DefaultFunctionDetailsGenerator(
            params,
            resultDummyVariable,
            functionLocation,
            0u,
            variablesLocation,
            IntermediateFormTreeNode.Const(0)
        ) { variableToRegisterMap[it]!! }

        val args = (0..7).map { IntermediateFormTreeNode.Const(it.toLong()) }.toList()
        val result = fdg.genCall(args)

        val expectedCFGBuilder = ControlFlowGraphBuilder()

        // write args
        for ((argRegister, arg) in argPositionToRegister zip args) {
            expectedCFGBuilder.addLinkFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IntermediateFormTreeNode.RegisterWrite(argRegister, arg)
            )
        }
        expectedCFGBuilder.addLinkFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.StackPush(args[7])
        )
        expectedCFGBuilder.addLinkFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.StackPush(args[6])
        )
        // call
        expectedCFGBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, IntermediateFormTreeNode.Call(functionLocation))

        // remove arguments previously put on stack
        expectedCFGBuilder.addLinkFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.Add(
                IntermediateFormTreeNode.RegisterRead(Register.RSP),
                IntermediateFormTreeNode.Const(16)
            )
        )

        val expectedResult = IntermediateFormTreeNode.RegisterRead(Register.RAX)
        val expected = expectedCFGBuilder.build()

        assert(expected.equalsByValue(result.callGraph))
        assertEquals(expectedResult, result.result)
    }

    @Test
    fun `test gen read direct from memory`() {
        val memVar: Variable = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IntermediateFormTreeNode.MemoryLabel(""),
            0u,
            mapOf(Pair(memVar, VariableLocationType.MEMORY)),
            IntermediateFormTreeNode.Const(0)
        )

        var readMemVar = fdg.genRead(memVar, true)

        assertTrue { readMemVar is IntermediateFormTreeNode.MemoryRead }

        readMemVar = readMemVar as IntermediateFormTreeNode.MemoryRead
        assertTrue { readMemVar.address is IntermediateFormTreeNode.Subtract }

        val left = (readMemVar.address as IntermediateFormTreeNode.Subtract).left
        val right = (readMemVar.address as IntermediateFormTreeNode.Subtract).right

        assertTrue { left is IntermediateFormTreeNode.RegisterRead }
        assertEquals(Register.RBP, (left as IntermediateFormTreeNode.RegisterRead).register)

        assertTrue { right is IntermediateFormTreeNode.Const }
        assertEquals(memoryUnitSize.toLong(), (right as IntermediateFormTreeNode.Const).value)
    }

    @Test
    fun `test gen read indirect from memory`() {
        val memVar: Variable = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)
        val displayAddress = IntermediateFormTreeNode.MemoryLabel("display")
        val depth: ULong = 5u
        val displayElementAddress = IntermediateFormTreeNode.Add(
            displayAddress,
            IntermediateFormTreeNode.Const((memoryUnitSize * depth).toLong())
        )

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IntermediateFormTreeNode.MemoryLabel(""),
            depth,
            mapOf(Pair(memVar, VariableLocationType.MEMORY)),
            displayAddress
        )

        var readMemVar = fdg.genRead(memVar, false)

        assertTrue { readMemVar is IntermediateFormTreeNode.MemoryRead }

        readMemVar = readMemVar as IntermediateFormTreeNode.MemoryRead
        assertTrue { readMemVar.address is IntermediateFormTreeNode.Subtract }

        val left = (readMemVar.address as IntermediateFormTreeNode.Subtract).left
        val right = (readMemVar.address as IntermediateFormTreeNode.Subtract).right

        println(left)
        assertEquals(IntermediateFormTreeNode.MemoryRead(displayElementAddress), left)

        assertTrue { right is IntermediateFormTreeNode.Const }
        assertEquals(memoryUnitSize.toLong(), (right as IntermediateFormTreeNode.Const).value)
    }

    @Test
    fun `test gen read direct from register`() {
        val regVar: Variable = Variable(Variable.Kind.VALUE, "regVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IntermediateFormTreeNode.MemoryLabel(""),
            0u,
            mapOf(Pair(regVar, VariableLocationType.REGISTER)),
            IntermediateFormTreeNode.Const(0)
        )

        val readRegVar = fdg.genRead(regVar, true)

        assertTrue { readRegVar is IntermediateFormTreeNode.RegisterRead }
    }

    @Test
    fun `test gen read indirect from register fails`() {
        val regVar: Variable = Variable(Variable.Kind.VALUE, "regVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IntermediateFormTreeNode.MemoryLabel(""),
            0u,
            mapOf(Pair(regVar, VariableLocationType.REGISTER)),
            IntermediateFormTreeNode.Const(0)
        )

        assertFailsWith<DefaultFunctionDetailsGenerator.IndirectRegisterAccess> { fdg.genRead(regVar, false) }
    }

    @Test
    fun `test gen write direct to memory`() {
        val memVar: Variable = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IntermediateFormTreeNode.MemoryLabel(""),
            0u,
            mapOf(Pair(memVar, VariableLocationType.MEMORY)),
            IntermediateFormTreeNode.Const(0)
        )

        val value = IntermediateFormTreeNode.Const(1)
        var writeMemVar = fdg.genWrite(memVar, value, true)

        assertTrue { writeMemVar is IntermediateFormTreeNode.MemoryWrite }

        writeMemVar = writeMemVar as IntermediateFormTreeNode.MemoryWrite
        assertTrue { writeMemVar.address is IntermediateFormTreeNode.Subtract }
        assertTrue { writeMemVar.value == value }

        val left = (writeMemVar.address as IntermediateFormTreeNode.Subtract).left
        val right = (writeMemVar.address as IntermediateFormTreeNode.Subtract).right

        assertTrue { left is IntermediateFormTreeNode.RegisterRead }
        assertEquals(Register.RBP, (left as IntermediateFormTreeNode.RegisterRead).register)

        assertTrue { right is IntermediateFormTreeNode.Const }
        assertEquals(memoryUnitSize.toLong(), (right as IntermediateFormTreeNode.Const).value)
    }

    @Test
    fun `test gen write indirect to memory`() {
        val memVar: Variable = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)
        val displayAddress = IntermediateFormTreeNode.MemoryLabel("display")
        val depth: ULong = 5u
        val displayElementAddress = IntermediateFormTreeNode.Add(
            displayAddress,
            IntermediateFormTreeNode.Const((memoryUnitSize * depth).toLong())
        )

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IntermediateFormTreeNode.MemoryLabel(""),
            depth,
            mapOf(Pair(memVar, VariableLocationType.MEMORY)),
            displayAddress
        )

        val value = IntermediateFormTreeNode.Const(1)
        var writeMemVar = fdg.genWrite(memVar, value, false)

        assertTrue { writeMemVar is IntermediateFormTreeNode.MemoryWrite }

        writeMemVar = writeMemVar as IntermediateFormTreeNode.MemoryWrite
        assertTrue { writeMemVar.address is IntermediateFormTreeNode.Subtract }
        assertTrue { writeMemVar.value == value }

        val left = (writeMemVar.address as IntermediateFormTreeNode.Subtract).left
        val right = (writeMemVar.address as IntermediateFormTreeNode.Subtract).right

        assertEquals(IntermediateFormTreeNode.MemoryRead(displayElementAddress), left)

        assertTrue { right is IntermediateFormTreeNode.Const }
        assertEquals(memoryUnitSize.toLong(), (right as IntermediateFormTreeNode.Const).value)
    }

    @Test
    fun `test gen write direct to register`() {
        val regVar: Variable = Variable(Variable.Kind.VALUE, "regVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IntermediateFormTreeNode.MemoryLabel(""),
            0u,
            mapOf(Pair(regVar, VariableLocationType.REGISTER)),
            IntermediateFormTreeNode.Const(0)
        )

        val value = IntermediateFormTreeNode.Const(1)
        val writeRegVal = fdg.genWrite(regVar, value, true)

        assertTrue { writeRegVal is IntermediateFormTreeNode.RegisterWrite }
        assertTrue { (writeRegVal as IntermediateFormTreeNode.RegisterWrite).node == value }
    }

    @Test
    fun `test gen write indirect to register fails`() {
        val regVar: Variable = Variable(Variable.Kind.VALUE, "regVar", Type.Number, null)

        val fdg = DefaultFunctionDetailsGenerator(
            listOf(),
            null,
            IntermediateFormTreeNode.MemoryLabel(""),
            0u,
            mapOf(Pair(regVar, VariableLocationType.REGISTER)),
            IntermediateFormTreeNode.Const(0)
        )

        val value = IntermediateFormTreeNode.Const(1)
        assertFailsWith<DefaultFunctionDetailsGenerator.IndirectRegisterAccess> { fdg.genWrite(regVar, value, false) }
    }
}
