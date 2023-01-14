package compiler.intermediate.generators

import compiler.ast.Expression
import compiler.ast.Type
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.IFTNode

interface ArrayMemoryManagement {
    fun genAllocation(size: IFTNode, initialization: List<IFTNode>, type: Type, mode: Expression.ArrayAllocation.InitializationType): Pair<ControlFlowGraph, IFTNode>
    fun genRefCountIncrement(address: IFTNode): ControlFlowGraph
    fun genRefCountDecrement(address: IFTNode, type: Type): ControlFlowGraph
}
