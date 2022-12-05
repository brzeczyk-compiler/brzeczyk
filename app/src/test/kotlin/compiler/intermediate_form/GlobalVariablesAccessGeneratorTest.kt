package compiler.intermediate_form

import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.reference_collections.referenceHashMapOf
import compiler.semantic_analysis.VariablePropertiesAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalVariablesAccessGeneratorTest {

    private fun createGlobalVariablesAccessGeneratorForVariables(varList: List<Variable>): GlobalVariablesAccessGenerator =
        referenceHashMapOf<Any, VariablePropertiesAnalyzer.VariableProperties>().apply {
            putAll(
                varList.associateWith {
                    VariablePropertiesAnalyzer.VariableProperties(
                        owner = VariablePropertiesAnalyzer.GlobalContext
                    )
                }
            )
        }.let {
            GlobalVariablesAccessGenerator(it)
        }

    private fun createProperMemoryRead(offset: Long): IntermediateFormTreeNode =
        IntermediateFormTreeNode.MemoryRead(
            IntermediateFormTreeNode.Add(
                IntermediateFormTreeNode.MemoryLabel("globals"),
                IntermediateFormTreeNode.Const(offset)
            )
        )

    private fun createProperMemoryWrite(offset: Long, value: IntermediateFormTreeNode) =
        IntermediateFormTreeNode.MemoryWrite(
            IntermediateFormTreeNode.Add(
                IntermediateFormTreeNode.MemoryLabel("globals"),
                IntermediateFormTreeNode.Const(offset)
            ),
            value
        )

    @Test
    fun `globals - read and write`() {
        val variable = Variable(Variable.Kind.VARIABLE, "x", Type.Number, null)
        val globalVariablesAccessGenerator =
            createGlobalVariablesAccessGeneratorForVariables(listOf(variable))

        assertEquals(
            globalVariablesAccessGenerator.genRead(variable, false),
            createProperMemoryRead(0)
        )

        val value = IntermediateFormTreeNode.Const(10)
        assertEquals(
            globalVariablesAccessGenerator.genWrite(variable, value, false),
            createProperMemoryWrite(0, value)
        )
    }

    @Test
    fun `globals - multiple variables`() {
        val variables = listOf("x", "y", "z").map { Variable(Variable.Kind.VARIABLE, it, Type.Number, null) }
        val globalVariablesAccessGenerator =
            createGlobalVariablesAccessGeneratorForVariables(variables)

        val readNodes = variables.map { globalVariablesAccessGenerator.genRead(it, false) }

        assertTrue(
            (0 until 3).all { index ->
                readNodes.filter { it == createProperMemoryRead(index * 4L) }.size == 1
            }
        )
    }
}
