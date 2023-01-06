package compiler.intermediate.generators

import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.IFTNode

interface GeneratorDetailsGenerator : VariableAccessGenerator {
    fun genInitCall(args: List<IFTNode>): FunctionDetailsGenerator.FunctionCallIntermediateForm

    fun genResumeCall(framePointer: IFTNode, savedState: IFTNode): FunctionDetailsGenerator.FunctionCallIntermediateForm

    fun genFinalizeCall(framePointer: IFTNode): FunctionDetailsGenerator.FunctionCallIntermediateForm

    fun genInit(): ControlFlowGraph

    fun genResume(mainBody: ControlFlowGraph): ControlFlowGraph

    fun genYield(value: IFTNode): ControlFlowGraph

    fun genFinalize(): ControlFlowGraph
}
