package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.Type
import compiler.ast.Variable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunctionDetailsGeneratorTest {
    private val functionLocation = IntermediateFormTreeNode.MemoryAddress("address")

    private val resultDummyVariable = Variable(Variable.Kind.VALUE, "numberResult", Type.Number, null)
    private val resultVariableRegister = Register()

    @Test
    fun `test genCall for zero argument, Unit function`() {
        val fdg = FunctionDetailsGenerator(
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
        val fdg = FunctionDetailsGenerator(
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
        val fdg = FunctionDetailsGenerator(
            listOf(),
            resultDummyVariable,
            functionLocation,
            0u,
            mapOf(resultDummyVariable to VariableLocationType.REGISTER),
            IntermediateFormTreeNode.Const(0)
        ) { variableToRegisterMap[it]!! }

        val expectedCFGBuilder = ControlFlowGraphBuilder(IntermediateFormTreeNode.Call(functionLocation))
        expectedCFGBuilder.addLinkFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.RegisterWrite(
                Register.RAX,
                IntermediateFormTreeNode.RegisterRead(resultVariableRegister)
            )
        )

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

        val fdg = FunctionDetailsGenerator(
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
            IntermediateFormTreeNode.StackPush(args.last())
        )
        expectedCFGBuilder.addLinkFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.StackPush(args[args.size - 2])
        )
        // call
        expectedCFGBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, IntermediateFormTreeNode.Call(functionLocation))

        // read result
        expectedCFGBuilder.addLinkFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.RegisterWrite(
                Register.RAX,
                IntermediateFormTreeNode.RegisterRead(resultVariableRegister)
            )
        )

        val expectedResult = IntermediateFormTreeNode.RegisterRead(Register.RAX)
        val expected = expectedCFGBuilder.build()

        assert(expected.equalsByValue(result.callGraph))
        assertEquals(expectedResult, result.result)
    }
}
