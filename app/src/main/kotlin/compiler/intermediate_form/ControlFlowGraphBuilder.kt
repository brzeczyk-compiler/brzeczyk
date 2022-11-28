package compiler.intermediate_form

import compiler.common.reference_collections.ReferenceHashMap

class ControlFlowGraphBuilder {
    private val unconditionalLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private val conditionalTrueLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private val conditionalFalseLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private val treeRoots = ArrayList<IFTNode>()
    private var entryTreeRoot: IFTNode? = null

    fun addLink(from: Pair<IFTNode, CFGLinkType>?, to: IFTNode) {
        if (from != null) {
            if (!treeRoots.contains(from.first))
                treeRoots.add(from.first)
            val links = when (from.second) {
                CFGLinkType.UNCONDITIONAL -> unconditionalLinks
                CFGLinkType.CONDITIONAL_TRUE -> conditionalTrueLinks
                CFGLinkType.CONDITIONAL_FALSE -> conditionalFalseLinks
            }
            links[from.first] = to
        } else
            entryTreeRoot = to
    }

    fun addAllFrom(cfg: ControlFlowGraph) {
        treeRoots.addAll(cfg.treeRoots)
        unconditionalLinks.putAll(cfg.unconditionalLinks)
        conditionalTrueLinks.putAll(cfg.conditionalTrueLinks)
        conditionalFalseLinks.putAll(cfg.conditionalFalseLinks)
    }

    fun build(): ControlFlowGraph {
        return ControlFlowGraph(
            treeRoots,
            entryTreeRoot,
            unconditionalLinks,
            conditionalTrueLinks,
            conditionalFalseLinks
        )
    }
}
