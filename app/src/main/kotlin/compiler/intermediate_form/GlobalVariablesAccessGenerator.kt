package compiler.intermediate_form

import compiler.ast.NamedNode
import compiler.ast.Variable
import compiler.common.intermediate_form.VariableAccessGenerator
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceMap
import compiler.semantic_analysis.VariablePropertiesAnalyzer

class GlobalVariablesAccessGenerator(variableProperties: ReferenceMap<Any, VariablePropertiesAnalyzer.VariableProperties>) : VariableAccessGenerator {

    companion object {
        const val VARIABLE_SIZE = 4
        const val GLOBALS_MEMORY_LABEL = "globals"
    }

    private val offsets = ReferenceHashMap<NamedNode, Long>().apply {
        this.putAll(
            variableProperties.filter { it.value.owner === VariablePropertiesAnalyzer.GlobalContext }
                .asIterable().mapIndexed { index, value -> value.key as Variable to index.toLong() * VARIABLE_SIZE }
        )
    }

    private fun getMemoryAddress(namedNode: NamedNode) =
        IntermediateFormTreeNode.Add(
            IntermediateFormTreeNode.MemoryLabel(GLOBALS_MEMORY_LABEL),
            IntermediateFormTreeNode.Const(offsets[namedNode]!!)
        )

    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IntermediateFormTreeNode =
        IntermediateFormTreeNode.MemoryRead(getMemoryAddress(namedNode))

    override fun genWrite(namedNode: NamedNode, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode =
        IntermediateFormTreeNode.MemoryWrite(getMemoryAddress(namedNode), value)
}
