package compiler.common.intermediate_form

import compiler.intermediate_form.ControlFlowGraph
import compiler.intermediate_form.IntermediateFormTreeNode

interface FunctionDetailsGeneratorInterface : VariableAccessGenerator {
    data class FunctionCallIntermediateForm(
        val callGraph: ControlFlowGraph,
        val result: IntermediateFormTreeNode?
    )

    fun generateCall(args: List<IntermediateFormTreeNode>): FunctionCallIntermediateForm

    fun genPrologue(): ControlFlowGraph

    fun genEpilogue(): ControlFlowGraph
}
