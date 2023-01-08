package compiler.intermediate.generators

import compiler.ast.Statement
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.IFTNode

interface GeneratorDetailsGenerator : VariableAccessGenerator {
    fun genInitCall(args: List<IFTNode>): FunctionDetailsGenerator.FunctionCallIntermediateForm

    fun genResumeCall(framePointer: IFTNode, savedState: IFTNode): FunctionDetailsGenerator.FunctionCallIntermediateForm

    fun genFinalizeCall(framePointer: IFTNode): FunctionDetailsGenerator.FunctionCallIntermediateForm

    fun genInit(): ControlFlowGraph

    fun genResume(mainBody: ControlFlowGraph): ControlFlowGraph

    fun genYield(value: IFTNode): ControlFlowGraph

    fun getNestedForeachFramePointerAddress(foreachLoop: Statement.ForeachLoop): IFTNode?

    fun genFinalize(): ControlFlowGraph

    val initFDG: FunctionDetailsGenerator

    val resumeFDG: FunctionDetailsGenerator

    val finalizeFDG: FunctionDetailsGenerator
}
