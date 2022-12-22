package compiler.intermediate.generators

import compiler.intermediate.ConstantPlaceholder
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.IFTNode

interface FunctionDetailsGenerator : VariableAccessGenerator {
    data class FunctionCallIntermediateForm(
        val callGraph: ControlFlowGraph,
        val result: IFTNode?
    )

    fun genCall(args: List<IFTNode>): FunctionCallIntermediateForm

    fun genPrologue(): ControlFlowGraph

    fun genEpilogue(): ControlFlowGraph

    val spilledRegistersOffset: ConstantPlaceholder
}
