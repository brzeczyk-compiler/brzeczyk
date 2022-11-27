package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceHashMap

data class FunctionCallIntermediateForm(
    val callGraph: ControlFlowGraph,
    val result: IntermediateFormTreeNode?
)

data class VariableDisplayInfo(
    val depth: ULong, // of parent function in display
    val offset: ULong // from rbp of parent function
)

enum class VariableLocationType {
    MEMORY,
    REGISTER
}

val FUNCTION_RESULT_REGISTER = Register()
val HELPER_REGISTER = Register()
val BASE_POINTER_REGISTER = Register()
val STACK_POINTER_REGISTER = Register()

data class FunctionDetailsGenerator(
    val depth: ULong,
    val variablesLocationTypes: Map<Variable, VariableLocationType>, // should contain parameters
    val parameters: List<Variable>,
    val functionCFG: ControlFlowGraph,
    val function: Function,
    val variablesDisplayInfo: MutableMap<String, VariableDisplayInfo>,
    val displayAddress: MemoryAddress
) {

    private val rbpInDisplayToRestore: MemoryAddress? = null
    private val variablesDisplayInfoToRestore: MutableMap<String, VariableDisplayInfo> = HashMap()
    private val variablesRegisters: MutableMap<String, Register> = ReferenceHashMap()
    var prologueOffsetFromRbp: ULong = 0u

    fun genCall(
        args: List<IntermediateFormTreeNode>,
    ): FunctionCallIntermediateForm {
        val cfgBuilder = ControlFlowGraphBuilder()
        var last: Pair<IFTNode, CFGLinkType>? = null

        // write arg values to params
        for ((arg, param) in args zip parameters) {
            val node = genWrite(param, arg, true)
            cfgBuilder.addAllFrom(node)
            cfgBuilder.addLink(last, node.entryTreeRoot!!)
            last = Pair(node.finalTreeRoots[0], CFGLinkType.UNCONDITIONAL)
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
        // save rbp
        val pushRbp = IntermediateFormTreeNode.StackPush(IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER))
        val movRbpRsp = IntermediateFormTreeNode.RegisterWrite(
            BASE_POINTER_REGISTER,
            IntermediateFormTreeNode.RegisterRead(STACK_POINTER_REGISTER)
        )

        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addLink(null, pushRbp)
        cfgBuilder.addLink(Pair(pushRbp, CFGLinkType.UNCONDITIONAL), movRbpRsp)
        var last: Pair<IFTNode, CFGLinkType> = Pair(movRbpRsp, CFGLinkType.UNCONDITIONAL)

        // allocate memory and registers for local variables
        for ((variable, locationType) in variablesLocationTypes.entries) {
            when (locationType) {

                VariableLocationType.MEMORY -> {
                    val node = IntermediateFormTreeNode.StackPush(IntermediateFormTreeNode.Const(0))
                    cfgBuilder.addLink(last, node)
                    last = Pair(node, CFGLinkType.UNCONDITIONAL)
                    prologueOffsetFromRbp += memorySize

                    // update variable display info
                    if (variablesDisplayInfo.containsKey(variable.name))
                        variablesDisplayInfoToRestore[variable.name] = variablesDisplayInfo[variable.name]!!
                    variablesDisplayInfo[variable.name] = VariableDisplayInfo(depth, prologueOffsetFromRbp)
                }

                VariableLocationType.REGISTER -> {
                    // create a register for the variable TODO: is some register assignment logic already needed here?
                    variablesRegisters[variable.name] = Register()
                }
            }
        }

        return cfgBuilder.build()
    }

    fun genEpilogue(): ControlFlowGraph {
        // abandon stack variables
        val subRspOffset = IntermediateFormTreeNode.RegisterWrite(
            STACK_POINTER_REGISTER,
            IntermediateFormTreeNode.Subtract(
                IntermediateFormTreeNode.RegisterRead(STACK_POINTER_REGISTER),
                IntermediateFormTreeNode.Const(prologueOffsetFromRbp.toLong())
            )
        )

        // restore rbp
        val movRspRbp = IntermediateFormTreeNode.RegisterWrite(
            STACK_POINTER_REGISTER,
            IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER)
        )
        val popRbp = IntermediateFormTreeNode.StackPopToRegister(BASE_POINTER_REGISTER)

        val cfgBuilder = ControlFlowGraphBuilder()
        cfgBuilder.addLink(null, subRspOffset)
        cfgBuilder.addLink(Pair(subRspOffset, CFGLinkType.UNCONDITIONAL), movRspRbp)
        cfgBuilder.addLink(Pair(movRspRbp, CFGLinkType.UNCONDITIONAL), popRbp)

        return cfgBuilder.build()
    }

    fun genRead(variable: Variable, isDirect: Boolean): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        if (isDirect) {
            cfgBuilder.addLink(
                null,
                when (variablesLocationTypes[variable]!!) {
                    VariableLocationType.MEMORY -> {
                        val offsetFromRbp = variablesDisplayInfo[variable.name]!!.offset
                        IntermediateFormTreeNode.MemoryRead(
                            Addressing.IndexAndDisplacement(BASE_POINTER_REGISTER, 1U, offsetFromRbp)
                        )
                    }
                    VariableLocationType.REGISTER ->
                        IntermediateFormTreeNode.RegisterRead(variablesRegisters[variable.name]!!)
                }
            )
        } else {
            val parentDisplayDepth = variablesDisplayInfo[variable.name]!!.depth
            val offsetFromParentRbp = variablesDisplayInfo[variable.name]!!.offset
            val displayElementAddress = displayAddress + memorySize * parentDisplayDepth

            val prepareHelperRegister = IntermediateFormTreeNode.RegisterWrite(
                HELPER_REGISTER,
                IntermediateFormTreeNode.MemoryRead(Addressing.Displacement(displayElementAddress)) // parent rbp
            )
            val valueRead = IntermediateFormTreeNode.MemoryRead(
                Addressing.IndexAndDisplacement(HELPER_REGISTER, 1U, offsetFromParentRbp)
            )

            cfgBuilder.addLink(null, prepareHelperRegister)
            cfgBuilder.addLink(Pair(prepareHelperRegister, CFGLinkType.UNCONDITIONAL), valueRead)
        }

        return cfgBuilder.build()
    }

    fun genWrite(variable: Variable, value: IntermediateFormTreeNode, isDirect: Boolean): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        if (isDirect) {
            cfgBuilder.addLink(
                null,
                when (variablesLocationTypes[variable]!!) {
                    VariableLocationType.MEMORY -> {
                        val offsetFromRbp = variablesDisplayInfo[variable.name]!!.offset
                        IntermediateFormTreeNode.MemoryWrite(
                            Addressing.IndexAndDisplacement(BASE_POINTER_REGISTER, 1U, offsetFromRbp),
                            value
                        )
                    }
                    VariableLocationType.REGISTER ->
                        IntermediateFormTreeNode.RegisterWrite(variablesRegisters[variable.name]!!, value)
                }
            )
        } else {
            val parentDisplayDepth = variablesDisplayInfo[variable.name]!!.depth
            val offsetFromParentRbp = variablesDisplayInfo[variable.name]!!.offset
            val displayElementAddress = displayAddress + memorySize * parentDisplayDepth

            val prepareHelperRegister = IntermediateFormTreeNode.RegisterWrite(
                HELPER_REGISTER,
                IntermediateFormTreeNode.MemoryRead(Addressing.Displacement(displayElementAddress)) // parent rbp
            )
            val valueWrite = IntermediateFormTreeNode.MemoryWrite(
                Addressing.IndexAndDisplacement(HELPER_REGISTER, 1U, offsetFromParentRbp),
                value
            )

            cfgBuilder.addLink(null, prepareHelperRegister)
            cfgBuilder.addLink(Pair(prepareHelperRegister, CFGLinkType.UNCONDITIONAL), valueWrite)
        }

        return cfgBuilder.build()
    }
}
