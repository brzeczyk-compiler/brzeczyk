package compiler.intermediate_form

import compiler.common.reference_collections.ReferenceHashMap

class ControlFlowGraphBuilder {
    private val unconditionalLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private val conditionalTrueLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private val conditionalFalseLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private val treeRoots = ArrayList<IFTNode>()
    private var entryTreeRoot: IFTNode? = null
    private val finalTreeRoots: List<IFTNode> get() = treeRoots.filter {
        it !in unconditionalLinks && it !in conditionalTrueLinks && it !in conditionalFalseLinks
    }

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

    fun updateFinalLinks(linkUpdate: (Triple<IFTNode, CFGLinkType, IFTNode>) -> Collection<Triple<IFTNode, CFGLinkType, IFTNode>>) {
        val linksToIterateOver = hashMapOf(
            CFGLinkType.UNCONDITIONAL to unconditionalLinks,
            CFGLinkType.CONDITIONAL_TRUE to conditionalTrueLinks,
            CFGLinkType.CONDITIONAL_FALSE to conditionalFalseLinks
        )

        val linksToAdd = ArrayList<Triple<IFTNode, CFGLinkType, IFTNode>>()

        for ((linkType, links) in linksToIterateOver)
            for ((from, to) in links)
                if (to in finalTreeRoots)
                    linksToAdd.addAll(linkUpdate(Triple(from, linkType, to)))

        for ((from, linkType, to) in linksToAdd)
            addLink(Pair(from, linkType), to)
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
