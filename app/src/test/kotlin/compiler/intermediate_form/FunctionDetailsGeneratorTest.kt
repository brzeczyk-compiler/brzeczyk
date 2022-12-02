package compiler.intermediate_form

import compiler.ast.Type
import compiler.ast.Variable
import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionDetailsGeneratorTest {
    private val functionLocation = IntermediateFormTreeNode.MemoryAddress("address")

    private val resultNumberVariable = Variable(Variable.Kind.VALUE, "numberResult", Type.Number, null)
    private val resultVariableRegister = Register()

    @Test
    fun `test genCall for zero argument, unit returning function`() {
        val fdg = FunctionDetailsGenerator(
            listOf(),
            null,
            functionLocation,
            0u,
            mapOf(),
            0u
        )
        val expected = ControlFlowGraphBuilder(IntermediateFormTreeNode.Call(functionLocation)).build()
        val result = fdg.genCall(listOf())

        assertEquals(expected, result.callGraph)
        assertEquals(null, result.result)
    }

    @Test
    fun `test genCall for zero argument Number returning function`() {
        val variableToRegisterMap = mapOf(resultNumberVariable to resultVariableRegister)
        val fdg = FunctionDetailsGenerator(
            listOf(),
            resultNumberVariable,
            functionLocation,
            0u,
            mapOf(resultNumberVariable to VariableLocationType.REGISTER),
            0u
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
}
