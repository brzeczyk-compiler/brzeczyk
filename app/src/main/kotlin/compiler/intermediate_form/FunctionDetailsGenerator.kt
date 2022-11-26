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
    val depth: Int,     // of parent function in display
    val offset: Long   // from rbp of parent function
)

enum class VariableLocationType {
    MEMORY,
    REGISTER
}

val FUNCTION_RESULT_REGISTER = Register()
val STACK_POINTER_REGISTER = Register()
val BASE_POINTER_REGISTER = Register()

data class FunctionDetailsGenerator(
    val depth: Int,
    val localVariables: Map<Variable, VariableLocationType>, // should contain parameters
    val variablesDisplayInfo: MutableMap<String, VariableDisplayInfo>,
    val parameters: List<Variable>,
    val functionCFG: ControlFlowGraph,
    val function: Function
) {

    private val rbpInDisplayToRestore: MemoryAddress? = null
    private val variablesDisplayInfoToRestore: MutableMap<String, VariableDisplayInfo> = HashMap()
    private val variablesRegisters: MutableMap<String, Register> = ReferenceHashMap()
    var prologueOffsetFromRbp: Long = 0

    fun genCall(
        args: List<IntermediateFormTreeNode>,
        display: MemoryAddress,
    ): FunctionCallIntermediateForm {
        val cfgBuilder = ControlFlowGraphBuilder()
        var last: Pair<IFTNode, CFGLinkType>? = null

        // write arg values to params
        for ((arg, param) in args zip parameters) {
            val node = genWrite(param, arg, true, display)
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

    fun genPrologue(display: MemoryAddress): ControlFlowGraph {
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

        // allocate memory and registers for local variables TODO maybe genCall is better for this?
        for ((variable, locationType) in localVariables.entries) {
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
                    // create a register for the variable TODO rethink this...
                    variablesRegisters[variable.name] = Register()
                }
            }
            }

        return cfgBuilder.build()
    }

    fun genEpilogue(display: MemoryAddress): ControlFlowGraph {
        // abandon stack variables
        val subRspOffset = IntermediateFormTreeNode.RegisterWrite(
            STACK_POINTER_REGISTER,
            IntermediateFormTreeNode.Subtract(
                IntermediateFormTreeNode.RegisterRead(STACK_POINTER_REGISTER),
                IntermediateFormTreeNode.Const(prologueOffsetFromRbp)
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

    fun genRead(variable: Variable, isDirect: Boolean, display: MemoryAddress): IntermediateFormTreeNode {
        return TODO()
    }

    fun genWrite(
        variable: Variable,
        value: IntermediateFormTreeNode,
        isDirect: Boolean,
        display: MemoryAddress,
    ): IntermediateFormTreeNode {
        return TODO()
    }
}
