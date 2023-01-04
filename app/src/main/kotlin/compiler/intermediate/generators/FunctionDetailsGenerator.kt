package compiler.intermediate.generators

import compiler.intermediate.ConstantPlaceholder
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.IFTNode

interface FunctionDetailsGenerator : VariableAccessGenerator {
    data class FunctionCallIntermediateForm(
        val callGraph: ControlFlowGraph,
        val result: IFTNode?,
        val stateForResumingGenerator: IFTNode?
    )

    // both normal and generator functions, caller-side
    fun genCall(args: List<IFTNode>): FunctionCallIntermediateForm

    // normal functions for ordinary calls and generators for resuming, callee-side
    fun genPrologue(): ControlFlowGraph
    fun genEpilogue(): ControlFlowGraph

    // generators, callee-side
    fun genInit(): ControlFlowGraph
    fun genFinalize(): ControlFlowGraph

    // generators, caller-side
    fun genYield(value: IFTNode): ControlFlowGraph

    val spilledRegistersRegionOffset: ULong

    val spilledRegistersRegionSize: ConstantPlaceholder

    val identifier: String
}
