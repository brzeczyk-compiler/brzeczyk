package compiler.intermediate.generators

import compiler.intermediate.ConstantPlaceholder
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.IFTNode

interface FunctionDetailsGenerator : VariableAccessGenerator {
    data class FunctionCallIntermediateForm(
        val callGraph: ControlFlowGraph,
        val result: IFTNode?,
        val secondResult: IFTNode? // eg. for storing state for further resuming generator functions
    )

    fun genCall(args: List<IFTNode>): FunctionCallIntermediateForm

    fun genPrologue(): ControlFlowGraph

    fun genEpilogue(): ControlFlowGraph

    val spilledRegistersRegionOffset: ULong

    val spilledRegistersRegionSize: ConstantPlaceholder

    val identifier: String
}
