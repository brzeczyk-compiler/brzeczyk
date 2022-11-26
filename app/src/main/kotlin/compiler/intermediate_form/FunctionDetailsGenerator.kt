package compiler.intermediate_form

import compiler.ast.Variable

data class FunctionDetailsGenerator(
    val depth: Int,
    val vars: Map<Variable, Boolean>,
    val parameters: List<Variable>,
    val functionCFG: ControlFlowGraph,
)
data class FunctionCallIntermediateForm(
    val callGraph: ControlFlowGraph,
    val result: List<IntermediateFormTreeNode> // function can have multiple exit points
)

fun generateCall(fdg: FunctionDetailsGenerator,  args: List<IntermediateFormTreeNode>): FunctionCallIntermediateForm {
    val cfgBuilder = ControlFlowGraphBuilder()

    var last: Pair<IFTNode, CFGLinkType>? = null

    // write arg values to params
    for((arg, param) in args zip fdg.parameters) {
        val node = genWrite(param, arg, true) //TODO: make sure `true` there is correct
        cfgBuilder.addLink(last, node)
        last = Pair(node, CFGLinkType.UNCONDITIONAL)
    }

    //add function graph
    cfgBuilder.addAllFrom(fdg.functionCFG)
    cfgBuilder.addLink(last, fdg.functionCFG.entryTreeRoot!!)

    fun removeReturnNodes(functionCfg: ControlFlowGraph): FunctionCallIntermediateForm {
        val finalNodes = functionCfg.finalTreeRoots

        val cfgBuilderForNoReturnCFG = ControlFlowGraphBuilder()

        val linksToIterateOver = hashMapOf(
            CFGLinkType.UNCONDITIONAL to functionCfg.unconditionalLinks,
            CFGLinkType.CONDITIONAL_TRUE to functionCfg.conditionalTrueLinks,
            CFGLinkType.CONDITIONAL_FALSE to functionCfg.conditionalFalseLinks
        )

        for((linkType, links) in linksToIterateOver){
            for ((from, to) in links) {
                if (to !in finalNodes)
                    cfgBuilderForNoReturnCFG.addLink(Pair(from, linkType), to)
            }
        }
       return FunctionCallIntermediateForm(
           cfgBuilderForNoReturnCFG.build(),
           finalNodes
       )
    }

    return removeReturnNodes(cfgBuilder.build())
}

fun genPrologue(fdg: FunctionDetailsGenerator): ControlFlowGraph {
    val cfgBuilder = ControlFlowGraphBuilder()
    var last: Pair<IFTNode, CFGLinkType>? = null
    for((variable, isInMemory) in fdg.vars.entries) {
        if(!isInMemory) {
            val node = IntermediateFormTreeNode.StackPush(genRead(variable, true))
            cfgBuilder.addLink(last, node)
            last = Pair(node, CFGLinkType.UNCONDITIONAL)
        }
    }
    return cfgBuilder.build()
}

fun genEpilogue(fdg: FunctionDetailsGenerator): ControlFlowGraph {
    val cfgBuilder = ControlFlowGraphBuilder()
    var last: Pair<IFTNode, CFGLinkType>? = null
    for((variable, isInMemory) in fdg.vars.entries.reversed()) {
        if(!isInMemory) {
            val node = genWrite(variable, IntermediateFormTreeNode.StackPop(), true)
            cfgBuilder.addLink(last, node)
            last = Pair(node, CFGLinkType.UNCONDITIONAL)
        }
    }
    return cfgBuilder.build()
}

fun genRead(variable: Variable, isDirect: Boolean): IntermediateFormTreeNode {
    // the caller is supposed to retrieve the value and assign it
    return TODO()
}

fun genWrite(variable: Variable, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode {
    return TODO()
}
