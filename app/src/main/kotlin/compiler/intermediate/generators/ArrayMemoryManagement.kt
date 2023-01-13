package compiler.intermediate.generators

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

    // frees array, given address of the first element
    fun genFree(address: IFTNode): ControlFlowGraph {
        val freeCall = freeFDG.genCall(listOf(IFTNode.Subtract(address, IFTNode.Const(2 * memoryUnitSize.toLong()))))
        return freeCall.callGraph
    }

    private val mallocFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("_\$checked_malloc"), 1)
    private val freeFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("free"), 0)
}
