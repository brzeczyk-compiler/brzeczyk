package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.Type
import compiler.ast.Variable

data class FunctionCallIntermediateForm(
    val callGraph: ControlFlowGraph,
    val result: IntermediateFormTreeNode?
)

val FUNCTION_RESULT_REGISTER = Register()

data class FunctionDetailsGenerator(
    val parameters: List<Variable>,
    val functionCFG: ControlFlowGraph,
    val function: Function
) {
    fun genCall(
        args: List<IntermediateFormTreeNode>,
    ): FunctionCallIntermediateForm {
        val cfgBuilder = ControlFlowGraphBuilder()

        var last: Pair<IFTNode, CFGLinkType>? = null
        // write arg values to params
        for ((arg, param) in args zip parameters) {
            val node = genWrite(param, arg, false)
            cfgBuilder.addAllFrom(node, false)
            cfgBuilder.addLink(last, node.entryTreeRoot!!)
            last = Pair(node.finalTreeRoots[0], CFGLinkType.UNCONDITIONAL)
        }

        // add function graph
        cfgBuilder.addAllFrom(functionCFG, false)
        cfgBuilder.addLink(last, functionCFG.entryTreeRoot!!)

        fun modifyReturnNodesToStoreResultInAppropriateRegister(functionCfg: ControlFlowGraph): FunctionCallIntermediateForm {
            val modifiedCfgBuilder = ControlFlowGraphBuilder()
            modifiedCfgBuilder.addAllFrom(functionCfg, true)
            modifiedCfgBuilder.updateNodes({ it in functionCfg.finalTreeRoots }, {
                IntermediateFormTreeNode.RegisterWrite(FUNCTION_RESULT_REGISTER, it)
            })

            return FunctionCallIntermediateForm(
                modifiedCfgBuilder.build(),
                IntermediateFormTreeNode.RegisterRead(FUNCTION_RESULT_REGISTER)
            )
        }

        if (function.returnType == Type.Unit)
            return FunctionCallIntermediateForm(cfgBuilder.build(), null)
        return modifyReturnNodesToStoreResultInAppropriateRegister(cfgBuilder.build())
    }
    fun genPrologue(): ControlFlowGraph { return TODO() }
    fun genEpilogue(): ControlFlowGraph { return TODO() }
    fun genRead(variable: Variable, isDirect: Boolean): ControlFlowGraph { return TODO() }
    fun genWrite(variable: Variable, value: IntermediateFormTreeNode, isDirect: Boolean): ControlFlowGraph { return TODO() }
}
