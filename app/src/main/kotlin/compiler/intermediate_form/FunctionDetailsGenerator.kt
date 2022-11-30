package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Type
import compiler.common.reference_collections.ReferenceHashMap

data class FunctionCallIntermediateForm(
    val callGraph: ControlFlowGraph,
    val result: IntermediateFormTreeNode?
)

enum class VariableLocationType {
    MEMORY,
    REGISTER
}

val FUNCTION_RESULT_REGISTER = Register()
val BASE_POINTER_REGISTER = Register()
val STACK_POINTER_REGISTER = Register()

data class FunctionDetailsGenerator(
    val depth: ULong,
    val variablesLocationTypes: Map<NamedNode, VariableLocationType>, // should contain parameters
    val parameters: List<Function.Parameter>,
    val functionCFG: ControlFlowGraph,
    val function: Function,
    val displayAddress: MemoryAddress
) {

    private val variablesStackOffsets: MutableMap<NamedNode, ULong> = ReferenceHashMap()
    private val variablesRegisters: MutableMap<NamedNode, Register> = ReferenceHashMap()
    private var prologueOffset: ULong = 0u

    fun genCall(
        args: List<IntermediateFormTreeNode>,
    ): FunctionCallIntermediateForm {
        val cfgBuilder = ControlFlowGraphBuilder()
        var last: Pair<IFTNode, CFGLinkType>? = null

        // write arg values to params
        for ((arg, param) in args zip parameters) {
            val node = genWrite(param, arg, true)
            cfgBuilder.addLink(last, node)
            last = Pair(node, CFGLinkType.UNCONDITIONAL)
        }

        // add function graph
        cfgBuilder.addAllFrom(functionCFG)
        cfgBuilder.addLink(last, functionCFG.entryTreeRoot!!)

        fun modifyReturnNodesToStoreResultInAppropriateRegister(functionCfg: ControlFlowGraph): FunctionCallIntermediateForm {
            val finalNodes = functionCfg.finalTreeRoots

            val modifiedCfgBuilder = ControlFlowGraphBuilder()

            val linksToIterateOver = hashMapOf(
                CFGLinkType.UNCONDITIONAL to functionCfg.unconditionalLinks,
                CFGLinkType.CONDITIONAL_TRUE to functionCfg.conditionalTrueLinks,
                CFGLinkType.CONDITIONAL_FALSE to functionCfg.conditionalFalseLinks
            )

            for ((linkType, links) in linksToIterateOver) {
                for ((from, to) in links) {
                    if (to !in finalNodes)
                        modifiedCfgBuilder.addLink(Pair(from, linkType), to)
                    else {
                        val newReturnNode = IntermediateFormTreeNode.RegisterWrite(FUNCTION_RESULT_REGISTER, to)
                        modifiedCfgBuilder.addLink(Pair(from, linkType), newReturnNode)
                    }
                }
            }
            return FunctionCallIntermediateForm(
                modifiedCfgBuilder.build(),
                IntermediateFormTreeNode.RegisterRead(FUNCTION_RESULT_REGISTER)
            )
        }

        if (function.returnType == Type.Unit)
            return FunctionCallIntermediateForm(cfgBuilder.build(), null)
        return modifyReturnNodesToStoreResultInAppropriateRegister(cfgBuilder.build())
    }

    fun genPrologue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // save rbp
        val pushRbp = IntermediateFormTreeNode.StackPush(IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER))
        val movRbpRsp = IntermediateFormTreeNode.RegisterWrite(
            BASE_POINTER_REGISTER,
            IntermediateFormTreeNode.RegisterRead(STACK_POINTER_REGISTER)
        )

        cfgBuilder.addLink(null, pushRbp)
        cfgBuilder.addLink(Pair(pushRbp, CFGLinkType.UNCONDITIONAL), movRbpRsp)
        var last: Pair<IFTNode, CFGLinkType> = Pair(movRbpRsp, CFGLinkType.UNCONDITIONAL)

        // update display
        val savePreviousRbp = IntermediateFormTreeNode.StackPush(
            IntermediateFormTreeNode.MemoryRead(
                IntermediateFormTreeNode.Const((displayAddress + memoryUnitSize * depth).toLong())
            )
        )
        val updateRbpAtDepth = IntermediateFormTreeNode.MemoryWrite(
            IntermediateFormTreeNode.Const((displayAddress + memoryUnitSize * depth).toLong()),
            IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER)
        )
        cfgBuilder.addLink(last, savePreviousRbp)
        last = Pair(savePreviousRbp, CFGLinkType.UNCONDITIONAL)
        cfgBuilder.addLink(last, updateRbpAtDepth)
        last = Pair(updateRbpAtDepth, CFGLinkType.UNCONDITIONAL)

        // allocate memory and registers for local variables
        for ((variable, locationType) in variablesLocationTypes.entries) {
            when (locationType) {

                VariableLocationType.MEMORY -> {
                    val node = IntermediateFormTreeNode.StackPush(IntermediateFormTreeNode.Const(0))
                    cfgBuilder.addLink(last, node)
                    last = Pair(node, CFGLinkType.UNCONDITIONAL)

                    variablesStackOffsets[variable] = 3u * memoryUnitSize + prologueOffset
                    prologueOffset += memoryUnitSize
                }

                VariableLocationType.REGISTER -> {
                    // create a register for the variable TODO: is some register assignment logic already needed here?
                    variablesRegisters[variable] = Register()
                }
            }
        }

        return cfgBuilder.build()
    }

    fun genEpilogue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // abandon stack variables
        val addRspOffset = IntermediateFormTreeNode.RegisterWrite(
            STACK_POINTER_REGISTER,
            IntermediateFormTreeNode.Add(
                IntermediateFormTreeNode.RegisterRead(STACK_POINTER_REGISTER),
                IntermediateFormTreeNode.Const(prologueOffset.toLong())
            )
        )

        cfgBuilder.addLink(null, addRspOffset)
        var last: Pair<IFTNode, CFGLinkType> = Pair(addRspOffset, CFGLinkType.UNCONDITIONAL)

        // restore previous rbp in display at depth
        val popPreviousRbp = IntermediateFormTreeNode.StackPopToMemory(
            IntermediateFormTreeNode.Const((displayAddress + memoryUnitSize * depth).toLong()),
        )

        cfgBuilder.addLink(last, popPreviousRbp)
        last = Pair(popPreviousRbp, CFGLinkType.UNCONDITIONAL)

        // restore rbp
        val movRspRbp = IntermediateFormTreeNode.RegisterWrite(
            STACK_POINTER_REGISTER,
            IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER)
        )
        val popRbp = IntermediateFormTreeNode.StackPopToRegister(BASE_POINTER_REGISTER)

        cfgBuilder.addLink(last, movRspRbp)
        last = Pair(movRspRbp, CFGLinkType.UNCONDITIONAL)
        cfgBuilder.addLink(last, popRbp)

        return cfgBuilder.build()
    }

    fun genRead(variable: NamedNode, isDirect: Boolean): IntermediateFormTreeNode {
        if (isDirect) {
            return when (variablesLocationTypes[variable]!!) {
                VariableLocationType.MEMORY -> {
                    IntermediateFormTreeNode.MemoryRead(
                        IntermediateFormTreeNode.Add(
                            IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER),
                            IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                        )
                    )
                }
                VariableLocationType.REGISTER ->
                    IntermediateFormTreeNode.RegisterRead(variablesRegisters[variable]!!)
            }
        } else {
            val displayElementAddress = displayAddress + memoryUnitSize * depth
            return IntermediateFormTreeNode.MemoryRead(
                IntermediateFormTreeNode.Add(
                    IntermediateFormTreeNode.MemoryRead(IntermediateFormTreeNode.Const(displayElementAddress.toLong())),
                    IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                )
            )
        }
    }

    fun genWrite(variable: NamedNode, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode {
        if (isDirect) {
            return when (variablesLocationTypes[variable]!!) {
                VariableLocationType.MEMORY -> {
                    IntermediateFormTreeNode.MemoryWrite(
                        IntermediateFormTreeNode.Add(
                            IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER),
                            IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                        ),
                        value
                    )
                }
                VariableLocationType.REGISTER ->
                    IntermediateFormTreeNode.RegisterWrite(variablesRegisters[variable]!!, value)
            }
        } else {
            val displayElementAddress = displayAddress + memoryUnitSize * depth
            return IntermediateFormTreeNode.MemoryWrite(
                IntermediateFormTreeNode.Add(
                    IntermediateFormTreeNode.MemoryRead(IntermediateFormTreeNode.Const(displayElementAddress.toLong())),
                    IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                ),
                value
            )
        }
    }
}
