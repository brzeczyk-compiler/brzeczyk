package compiler.common.intermediate_form

import compiler.ast.Variable
import compiler.intermediate_form.ControlFlowGraph
import compiler.intermediate_form.IntermediateFormTreeNode

interface FunctionDetailsGeneratorInterface {
    data class FunctionCallIntermediateForm(
        val callGraph: ControlFlowGraph,
        val result: IntermediateFormTreeNode?
    )

    fun generateCall(args: List<IntermediateFormTreeNode>): FunctionCallIntermediateForm

    fun genPrologue(): ControlFlowGraph

    fun genEpilogue(): ControlFlowGraph

    fun genRead(variable: Variable, isDirect: Boolean): IntermediateFormTreeNode

    fun genWrite(variable: Variable, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode
}
