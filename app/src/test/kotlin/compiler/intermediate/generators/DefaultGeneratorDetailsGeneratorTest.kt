package compiler.intermediate.generators

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.FixedConstant
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import compiler.utils.Ref
import compiler.utils.keyRefMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultGeneratorDetailsGeneratorTest {
    private val initLabel = IFTNode.MemoryLabel("generatorNameInit")
    private val resumeLabel = IFTNode.MemoryLabel("generatorNameResume")
    private val finalizeLabel = IFTNode.MemoryLabel("generatorNameFinalize")

    private val depth: ULong = 3U
    private val displayAddress = IFTNode.Dummy()

    private val argumentNode1 = IFTNode.Dummy()
    private val argumentNode2 = IFTNode.Dummy()

    private val callerSavedRegisters = listOf(Register.RAX, Register.RCX, Register.RDX, Register.RSI, Register.RDI, Register.R8, Register.R9, Register.R10, Register.R11)
    private val argumentPassingRegisters = listOf(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.R8, Register.R9)

    @Test
    fun `test genInitCall`() {
        val params = (0..7).map { Function.Parameter("param$it", Type.Number, null, null) }.toList()
        val gdg = DefaultGeneratorDetailsGenerator(
            params,
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            params.associate { Ref(it) to VariableLocationType.MEMORY },
            displayAddress,
            emptyList(),
            emptyMap(),
            emptyMap()
        )
        val args = (0..7).map { IFTNode.Const(it.toLong()) }.toList()

        val expectedCFGBuilder = ControlFlowGraphBuilder()
        for ((register, node) in argumentPassingRegisters zip args)
            expectedCFGBuilder.addSingleTree(IFTNode.RegisterWrite(register, node))
        expectedCFGBuilder.addSingleTree(IFTNode.StackPush(args[7]))
        expectedCFGBuilder.addSingleTree(IFTNode.StackPush(args[6]))
        expectedCFGBuilder.addSingleTree(IFTNode.Call(initLabel, argumentPassingRegisters, callerSavedRegisters))
        expectedCFGBuilder.addSingleTree(
            IFTNode.RegisterWrite(
                Register.RSP,
                IFTNode.Add(IFTNode.RegisterRead(Register.RSP), IFTNode.Const(16))
            )
        )

        val expectedCFG = expectedCFGBuilder.build()
        val expectedFirstResult = IFTNode.RegisterRead(Register.RAX)
        val result = gdg.genInitCall(args)
        assert(expectedCFG.isIsomorphicTo(result.callGraph))
        assertEquals(expectedFirstResult, result.result)
        assertNull(result.secondResult)
    }

    @Test
    fun `test genResumeCall`() {
        val gdg = DefaultGeneratorDetailsGenerator(
            emptyList(),
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            emptyMap(),
            displayAddress,
            emptyList(),
            emptyMap(),
            emptyMap()
        )

        val expectedCFGBuilder = ControlFlowGraphBuilder()
        expectedCFGBuilder.addSingleTree(IFTNode.RegisterWrite(Register.RDI, argumentNode1))
        expectedCFGBuilder.addSingleTree(IFTNode.RegisterWrite(Register.RSI, argumentNode2))
        expectedCFGBuilder.addSingleTree(
            IFTNode.Call(
                resumeLabel,
                listOf(Register.RDI, Register.RSI),
                callerSavedRegisters
            )
        )

        val expectedCFG = expectedCFGBuilder.build()
        val expectedFirstResult = IFTNode.RegisterRead(Register.RAX)
        val expectedSecondResult = IFTNode.RegisterRead(Register.RDX)
        val result = gdg.genResumeCall(argumentNode1, argumentNode2)
        assert(expectedCFG.isIsomorphicTo(result.callGraph))
        assertEquals(expectedFirstResult, result.result)
        assertEquals(expectedSecondResult, result.secondResult)
    }

    @Test
    fun `test genFinalizeCall`() {
        val gdg = DefaultGeneratorDetailsGenerator(
            emptyList(),
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            emptyMap(),
            displayAddress,
            emptyList(),
            emptyMap(),
            emptyMap()
        )

        val expectedCFGBuilder = ControlFlowGraphBuilder()
        expectedCFGBuilder.addSingleTree(IFTNode.RegisterWrite(Register.RDI, argumentNode1))
        expectedCFGBuilder.addSingleTree(
            IFTNode.Call(
                finalizeLabel,
                listOf(Register.RDI),
                callerSavedRegisters
            )
        )

        val expectedCFG = expectedCFGBuilder.build()
        val result = gdg.genFinalizeCall(argumentNode1)
        assert(expectedCFG.isIsomorphicTo(result.callGraph))
        assertNull(result.result)
        assertNull(result.secondResult)
    }

    @Test
    fun `test gen read direct from memory`() {
        val memVar = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)

        val gdg = DefaultGeneratorDetailsGenerator(
            listOf(),
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            keyRefMapOf(memVar to VariableLocationType.MEMORY),
            displayAddress,
            emptyList(),
            emptyMap(),
            emptyMap()
        )

        var readMemVar = gdg.genRead(memVar, true)

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
        val displayElementAddress = IFTNode.Add(
            displayAddress,
            IFTNode.Const((memoryUnitSize * depth).toLong())
        )

        val gdg = DefaultGeneratorDetailsGenerator(
            listOf(),
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            keyRefMapOf(memVar to VariableLocationType.MEMORY),
            displayAddress,
            emptyList(),
            emptyMap(),
            emptyMap()
        )

        var readMemVar = gdg.genRead(memVar, false)

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

        val gdg = DefaultGeneratorDetailsGenerator(
            listOf(),
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            keyRefMapOf(regVar to VariableLocationType.REGISTER),
            displayAddress,
            emptyList(),
            emptyMap(),
            emptyMap()
        )

        val readRegVar = gdg.genRead(regVar, true)

        assertTrue { readRegVar is IFTNode.RegisterRead }
    }

    @Test
    fun `test gen read indirect from register fails`() {
        val regVar = Variable(Variable.Kind.VALUE, "regVar", Type.Number, null)

        val gdg = DefaultGeneratorDetailsGenerator(
            listOf(),
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            keyRefMapOf(regVar to VariableLocationType.REGISTER),
            displayAddress,
            emptyList(),
            emptyMap(),
            emptyMap()
        )

        assertFailsWith<DefaultFunctionDetailsGenerator.IndirectRegisterAccess> { gdg.genRead(regVar, false) }
    }

    @Test
    fun `test gen write direct to memory`() {
        val memVar = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)

        val gdg = DefaultGeneratorDetailsGenerator(
            listOf(),
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            keyRefMapOf(memVar to VariableLocationType.MEMORY),
            displayAddress,
            emptyList(),
            emptyMap(),
            emptyMap()
        )

        val value = IFTNode.Const(1)
        var writeMemVar = gdg.genWrite(memVar, value, true)

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
        val memVar = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)
        val displayElementAddress = IFTNode.Add(
            displayAddress,
            IFTNode.Const((memoryUnitSize * depth).toLong())
        )

        val gdg = DefaultGeneratorDetailsGenerator(
            listOf(),
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            keyRefMapOf(memVar to VariableLocationType.MEMORY),
            displayAddress,
            emptyList(),
            emptyMap(),
            emptyMap()
        )

        val value = IFTNode.Const(1)
        var writeMemVar = gdg.genWrite(memVar, value, false)

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
        val regVar = Variable(Variable.Kind.VALUE, "regVar", Type.Number, null)

        val gdg = DefaultGeneratorDetailsGenerator(
            listOf(),
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            keyRefMapOf(regVar to VariableLocationType.REGISTER),
            displayAddress,
            emptyList(),
            emptyMap(),
            emptyMap()
        )

        val value = IFTNode.Const(1)
        val writeRegVal = gdg.genWrite(regVar, value, true)

        assertTrue { writeRegVal is IFTNode.RegisterWrite }
        assertTrue { (writeRegVal as IFTNode.RegisterWrite).node == value }
    }

    @Test
    fun `test gen write indirect to register fails`() {
        val regVar = Variable(Variable.Kind.VALUE, "regVar", Type.Number, null)

        val gdg = DefaultGeneratorDetailsGenerator(
            listOf(),
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            keyRefMapOf(regVar to VariableLocationType.REGISTER),
            displayAddress,
            emptyList(),
            emptyMap(),
            emptyMap()
        )

        val value = IFTNode.Const(1)
        assertFailsWith<DefaultFunctionDetailsGenerator.IndirectRegisterAccess> { gdg.genWrite(regVar, value, false) }
    }

    @Test
    fun `test get nested foreach pointer`() {
        val memVar = Variable(Variable.Kind.VALUE, "memVar", Type.Number, null)
        val nestedForeach = Statement.ForeachLoop(
            Variable(Variable.Kind.VALUE, "dummy", Type.Number, null),
            Expression.FunctionCall("dummy", emptyList()),
            emptyList()
        )

        val gdg = DefaultGeneratorDetailsGenerator(
            listOf(),
            initLabel,
            resumeLabel,
            finalizeLabel,
            depth,
            keyRefMapOf(memVar to VariableLocationType.MEMORY),
            displayAddress,
            listOf(Ref(nestedForeach)),
            emptyMap(),
            emptyMap()
        )
        gdg.resumeFDG.spilledRegistersRegionSize.settledValue = 8

        val result = gdg.getNestedForeachFramePointerAddress(nestedForeach)
        assertNotNull(result)
        assertIs<IFTNode.Subtract>(result)
        assertEquals(IFTNode.RegisterRead(Register.RBP), result.left)
        assertIs<IFTNode.Const>(result.right)
        assertEquals(24, (result.right as IFTNode.Const).value.value)
    }
}
