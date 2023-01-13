package compiler.intermediate.generators

import compiler.ast.Type
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.IFTNode

object ArrayMemoryManagement {

    // allocates new array, returns cfg and address of the first element
    fun genAllocation(size: Int, initialization: List<IFTNode>): Pair<ControlFlowGraph, IFTNode> {
        val cfgBuilder = ControlFlowGraphBuilder()

        val mallocCall = mallocFDG.genCall(listOf(IFTNode.Const((size + 2) * memoryUnitSize.toLong())))
        cfgBuilder.mergeUnconditionally(mallocCall.callGraph)
        cfgBuilder.addSingleTree(
            IFTNode.MemoryWrite(
                IFTNode.Add(mallocCall.result!!, IFTNode.Const(memoryUnitSize.toLong())),
                IFTNode.Const(size.toLong())
            )
        )
        initialization.forEachIndexed { index, iftNode ->
            cfgBuilder.addSingleTree(
                IFTNode.MemoryWrite(
                    IFTNode.Add(mallocCall.result, IFTNode.Const((index + 2) * memoryUnitSize.toLong())),
                    iftNode
                )
            )
        }
        return Pair(cfgBuilder.build(), IFTNode.Add(mallocCall.result, IFTNode.Const(2 * memoryUnitSize.toLong())))
    }

    fun genRefCountIncrement(address: IFTNode): Unit = TODO()

    fun genRefCountDecrement(address: IFTNode, type: Type): ControlFlowGraph {
        fun getArrayLevel(type: Type): Int = when (type) {
            is Type.Array -> 1 + getArrayLevel(type.elementType)
            else -> 0
        }

        val level = getArrayLevel(type)
        return refCountDecrementFDG.genCall(listOf(address, IFTNode.Const(level.toLong()))).callGraph
    }

    private val mallocFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("_\$checked_malloc"), 1)
    private val refCountDecrementFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("_\$array_ref_count_decrement"), 0)
}
