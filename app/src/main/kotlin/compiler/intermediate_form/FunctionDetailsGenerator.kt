package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceMap

data class FunctionCallIntermediateForm(
    val callGraph: ControlFlowGraph,
    val result: IntermediateFormTreeNode?
)

data class VariableLocation(
    val depth: Int,
    val offset: ULong
)

val FUNCTION_RESULT_REGISTER = Register()
val STACK_POINTER_REGISTER = Register()
val BASE_POINTER_REGISTER = Register()

data class FunctionDetailsGenerator(
    val depth: Int,
    val vars: Map<Variable, Boolean>,
    val parameters: List<Variable>,
    val functionCFG: ControlFlowGraph,
    val function: Function
) {
    fun generateCall(
        args: List<IntermediateFormTreeNode>,
        display: MemoryAddress,
        variablesLocations: ReferenceMap<Variable, VariableLocation>
    ): FunctionCallIntermediateForm {
        val cfgBuilder = ControlFlowGraphBuilder()

        var last: Pair<IFTNode, CFGLinkType>? = null

        // write arg values to params
        for ((arg, param) in args zip parameters) {
            val node = genWrite(param, arg, true, display, variablesLocations)
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

    fun genPrologue(
        display: MemoryAddress,
        variablesLocations: ReferenceMap<Variable, VariableLocation>
    ): ControlFlowGraph {
        return TODO()
    }

    fun genEpilogue(
        display: MemoryAddress,
        variablesLocations: ReferenceMap<Variable, VariableLocation>
    ): ControlFlowGraph {
        return TODO()
    }

    fun genRead(
        variable: Variable,
        isDirect: Boolean,
        display: MemoryAddress,
        variablesLocations: ReferenceMap<Variable, VariableLocation>
    ): IntermediateFormTreeNode {
        // the caller is supposed to retrieve the value and assign it
        return TODO()
    }

    fun genWrite(
        variable: Variable,
        value: IntermediateFormTreeNode,
        isDirect: Boolean,
        display: MemoryAddress,
        variablesLocations: ReferenceMap<Variable, VariableLocation>
    ): IntermediateFormTreeNode {
        return TODO()
    }
}
