package compiler.intermediate.generators

import compiler.ast.Expression
import compiler.ast.Type
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.IFTNode
import compiler.intermediate.Register

object DefaultArrayMemoryManagement : ArrayMemoryManagement {

    // allocates new array, returns cfg and address of the first element
    override fun genAllocation(size: IFTNode, initialization: List<IFTNode>, type: Type, mode: Expression.ArrayAllocation.InitializationType): Pair<ControlFlowGraph, IFTNode> {
        val cfgBuilder = ControlFlowGraphBuilder()
        val elementType = (type as Type.Array).elementType
        val tableSize = when (mode) {
            Expression.ArrayAllocation.InitializationType.ONE_VALUE -> IFTNode.Add(size, IFTNode.Const(2))
            Expression.ArrayAllocation.InitializationType.ALL_VALUES -> IFTNode.Const(initialization.size + 2L)
        }
        val sizeArg = IFTNode.Multiply(tableSize, IFTNode.Const(memoryUnitSize.toLong()))

        val mallocCall = mallocFDG.genCall(listOf(sizeArg))
        cfgBuilder.mergeUnconditionally(mallocCall.callGraph)
        val temporaryResultRegister = Register()
        cfgBuilder.addSingleTree(IFTNode.RegisterWrite(temporaryResultRegister, mallocCall.result!!))

        fun writeAt(index: Long, value: IFTNode) {
            cfgBuilder.addSingleTree(
                IFTNode.MemoryWrite(
                    IFTNode.Add(IFTNode.RegisterRead(temporaryResultRegister), IFTNode.Const(index * memoryUnitSize.toLong())),
                    value
                )
            )
        }

        writeAt(0L, IFTNode.Const(1L))
        writeAt(1L, tableSize)
        val publicArrayAddress = IFTNode.Add(
            IFTNode.RegisterRead(temporaryResultRegister),
            IFTNode.Const(2 * memoryUnitSize.toLong())
        )

        when (mode) {
            Expression.ArrayAllocation.InitializationType.ALL_VALUES -> {
                initialization.forEachIndexed { index, iftNode ->
                    writeAt(index + 2L, iftNode)
                    if (elementType is Type.Array) {
                        cfgBuilder.mergeUnconditionally(genRefCountIncrement(iftNode))
                    }
                }
            }

            Expression.ArrayAllocation.InitializationType.ONE_VALUE -> {
                val initElement = initialization.first()
                val shouldIncrementElements = if (elementType is Type.Array) 1L else 0L
                cfgBuilder.mergeUnconditionally(
                    dynamicPopulateFDG.genCall(
                        listOf(publicArrayAddress, initElement, IFTNode.Const(shouldIncrementElements))
                    ).callGraph
                )
            }
        }

        return Pair(cfgBuilder.build(), publicArrayAddress)
    }

    override fun genRefCountIncrement(address: IFTNode): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()
        val refCountAddress = IFTNode.Subtract(address, IFTNode.Const(2 * memoryUnitSize.toLong()))
        cfgBuilder.addSingleTree(
            IFTNode.MemoryWrite(refCountAddress, IFTNode.Add(IFTNode.Const(1), IFTNode.MemoryRead(refCountAddress)))
        )
        return cfgBuilder.build()
    }

    override fun genRefCountDecrement(address: IFTNode, type: Type): ControlFlowGraph {
        fun getArrayLevel(type: Type): Int = when (type) {
            is Type.Array -> 1 + getArrayLevel(type.elementType)
            else -> 0
        }

        val level = getArrayLevel(type)
        return refCountDecrementFDG.genCall(listOf(address, IFTNode.Const(level.toLong()))).callGraph
    }

    private val mallocFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("_\$checked_malloc"), 1)
    private val dynamicPopulateFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("_\$populate_dynamic_array"), 0)
    private val refCountDecrementFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("_\$array_ref_count_decrement"), 0)
}
