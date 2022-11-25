package compiler.intermediate_form

import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceHashMap

data class FunctionDetailsGenerator(
    val depth: Int,
    val vars: Map<Variable, Boolean>,
    val parameters: List<Variable>,
    val functionCFG: ControlFlowGraph,
)
data class FunctionCallIntermediateForm(
    val callGraph: ControlFlowGraph,
    val result: IntermediateFormTreeNode?
)

fun generateCall(fdg: FunctionDetailsGenerator,  args: List<IntermediateFormTreeNode>): FunctionCallIntermediateForm {
    val cfgBuilder = ControlFlowGraphBuilder()

    // prologue
    val prologueCFG = genPrologue(fdg)
    cfgBuilder.addAllFrom(prologueCFG)
    assert(prologueCFG.finalTreeRoots.size == 1)
    var last = prologueCFG.finalTreeRoots[0]

    // write arg values to params
    for((arg, param) in args zip fdg.parameters) {
        val node = genWrite(param, arg, true)
        cfgBuilder.addLink(Pair(last, CFGLinkType.UNCONDITIONAL), node)
        last = node
    }

    //add function graph
    cfgBuilder.addAllFrom(fdg.functionCFG)
    cfgBuilder.addLink(Pair(last, CFGLinkType.UNCONDITIONAL), fdg.functionCFG.entryTreeRoot!!)

    val cfgEpilogue = genEpilogue()
    cfgBuilder.addAllFrom(cfgEpilogue)
    for(functionFinalTreeRoot in fdg.functionCFG.finalTreeRoots)
        cfgBuilder.addLink(Pair(functionFinalTreeRoot, CFGLinkType.UNCONDITIONAL), cfgEpilogue.entryTreeRoot!!)
    return FunctionCallIntermediateForm(cfgBuilder.build(), null)
}

fun genPrologue(fdg: FunctionDetailsGenerator): ControlFlowGraph {
    val last: IntermediateFormTreeNode? = null
    val unconditionalLinks = ReferenceHashMap<IFTNode, IFTNode>()
    val conditionalTrueLinks = ReferenceHashMap<IFTNode, IFTNode>()
    val conditionalFalseLinks = ReferenceHashMap<IFTNode, IFTNode>()
    val treeRoots = ArrayList<IFTNode>()
    var entryTreeRoot: IFTNode? = null

    fun addLinkToLast(newNode: IntermediateFormTreeNode) {
        treeRoots.add(newNode)
        if (last != null)
            unconditionalLinks[last] = newNode
        else
            entryTreeRoot = newNode
    }

    for((variable, isInMemory) in fdg.vars.entries) {
        if(!isInMemory) {
            val node = IntermediateFormTreeNode.StackPush(genRead(variable, true))
            addLinkToLast(node)
        }
    }
    return ControlFlowGraph(
        treeRoots,
        entryTreeRoot,
        unconditionalLinks,
        conditionalTrueLinks,
        conditionalFalseLinks
    )
}

fun genEpilogue(): ControlFlowGraph {
    return TODO()
}

fun genRead(variable: Variable, isDirect: Boolean): IntermediateFormTreeNode {
    // the caller is supposed to retrieve the value and assign it
    return TODO()
}

fun genWrite(variable: Variable, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode {
    return TODO()
}
