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

        fun writeAt(index: Int, value: IFTNode) {
            cfgBuilder.addSingleTree(
                IFTNode.MemoryWrite(
                    IFTNode.Add(mallocCall.result!!, IFTNode.Const(index * memoryUnitSize.toLong())),
                    value
                )
            )
        }

        fun writeAt(index: Int, value: Long) = writeAt(index, IFTNode.Const(value))

        writeAt(0, 1)
        writeAt(1, size.toLong())

        if (size == initialization.size) {
            initialization.forEachIndexed { index, iftNode -> writeAt(index + 2, iftNode) }
        } else { // asserts initialization has exactly one element
            val initElement = initialization.first()
            (2 until 2 + size).forEach { writeAt(it, initElement) }
        }

        return Pair(cfgBuilder.build(), IFTNode.Add(mallocCall.result!!, IFTNode.Const(2 * memoryUnitSize.toLong())))
    }

    fun genRefCountIncrement(address: IFTNode): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()
        val refCountAddress = IFTNode.Subtract(address, IFTNode.Const(2 * memoryUnitSize.toLong()))
        cfgBuilder.addSingleTree(
            IFTNode.MemoryWrite(refCountAddress, IFTNode.Add(IFTNode.Const(1), IFTNode.MemoryRead(refCountAddress)))
        )
        return cfgBuilder.build()
    }

    fun genRefCountDecrement(address: IFTNode, type: Type): ControlFlowGraph = throw NotImplementedError()

    private val mallocFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("_\$checked_malloc"), 1)
}
