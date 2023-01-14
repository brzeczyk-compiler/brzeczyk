package compiler.intermediate.generators

import compiler.ast.Type
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.IFTNode

interface ArrayMemoryManagement {
    fun genAllocation(size: Int, initialization: List<IFTNode>, type: Type): Pair<ControlFlowGraph, IFTNode>
    fun genRefCountIncrement(address: IFTNode): ControlFlowGraph
    fun genRefCountDecrement(address: IFTNode, type: Type): ControlFlowGraph
}
