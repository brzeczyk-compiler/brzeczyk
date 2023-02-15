package compiler.intermediate.generators

import compiler.analysis.VariablePropertiesAnalyzer
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Type
import compiler.ast.Variable
import compiler.intermediate.IFTNode
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalVariableAccessGeneratorTest {

    private fun createGlobalVariablesAccessGeneratorForVariables(varList: List<Variable>): GlobalVariableAccessGenerator =
        mutableKeyRefMapOf<AstNode, VariablePropertiesAnalyzer.VariableProperties>().apply {
            putAll(
                varList.associate {
                    Ref(it) to VariablePropertiesAnalyzer.VariableProperties(owner = null)
                }
            )
        }.let {
            GlobalVariableAccessGenerator(it)
        }

    private fun createProperMemoryRead(offset: Long): IFTNode =
        IFTNode.MemoryRead(
            IFTNode.Add(
                IFTNode.MemoryLabel("globals"),
                IFTNode.Const(offset)
            )
        )

    private fun createProperMemoryWrite(offset: Long, value: IFTNode) =
        IFTNode.MemoryWrite(
            IFTNode.Add(
                IFTNode.MemoryLabel("globals"),
                IFTNode.Const(offset)
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

        val value = IFTNode.Const(10)
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
                readNodes.filter { it == createProperMemoryRead(index * GlobalVariableAccessGenerator.VARIABLE_SIZE) }.size == 1
            }
        )
    }

    @Test
    fun `globals - read constant`() {
        val variable = Variable(Variable.Kind.CONSTANT, "x", Type.Number, Expression.NumberLiteral(15))
        val globalVariablesAccessGenerator =
            createGlobalVariablesAccessGeneratorForVariables(listOf(variable))

        assertEquals(
            globalVariablesAccessGenerator.genRead(variable, false),
            IFTNode.Const(15)
        )
    }
}
