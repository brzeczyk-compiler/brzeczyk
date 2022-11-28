package compiler.intermediate_form

import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.referenceHashMapOf

class ControlFlowGraphBuilder(var entryTreeRoot: IFTNode? = null) {
    private var unconditionalLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private var conditionalTrueLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private var conditionalFalseLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private var treeRoots = ArrayList<IFTNode>()

    init {
        if (entryTreeRoot != null)
            treeRoots.add(entryTreeRoot!!)
    }

    fun addLink(from: Pair<IFTNode, CFGLinkType>?, to: IFTNode) {
        if (from != null) {
            for (node in listOf(from.first, to))
                if (!treeRoots.contains(node))
                    treeRoots.add(node)

            val links = when (from.second) {
                CFGLinkType.UNCONDITIONAL -> unconditionalLinks
                CFGLinkType.CONDITIONAL_TRUE -> conditionalTrueLinks
                CFGLinkType.CONDITIONAL_FALSE -> conditionalFalseLinks
            }
            links[from.first] = to
        } else {
            entryTreeRoot = to
            if (!treeRoots.contains(to))
                treeRoots.add(to)
        }
    }

    fun addAllFrom(cfg: ControlFlowGraph, modifyEntryTreeRoot: Boolean) {
        treeRoots.addAll(cfg.treeRoots)
        if (modifyEntryTreeRoot)
            entryTreeRoot = cfg.entryTreeRoot
        unconditionalLinks.putAll(cfg.unconditionalLinks)
        conditionalTrueLinks.putAll(cfg.conditionalTrueLinks)
        conditionalFalseLinks.putAll(cfg.conditionalFalseLinks)
    }

    fun updateNodes(nodeFilter: (IFTNode) -> Boolean, nodeUpdate: (IFTNode) -> IFTNode) {
        val newTreeRoots = treeRoots.associateWith {
            if (nodeFilter(it)) nodeUpdate(it) else it
        }

        fun linkReplacer(fromAndTo: Map.Entry<IFTNode, IFTNode>): Pair<IFTNode, IFTNode> {
            return Pair(newTreeRoots[fromAndTo.key]!!, newTreeRoots[fromAndTo.value]!!)
        }

        unconditionalLinks = referenceHashMapOf(*unconditionalLinks.map { linkReplacer(it) }.toTypedArray())
        conditionalTrueLinks = referenceHashMapOf(*conditionalTrueLinks.map { linkReplacer(it) }.toTypedArray())
        conditionalFalseLinks = referenceHashMapOf(*conditionalFalseLinks.map { linkReplacer(it) }.toTypedArray())
        entryTreeRoot = newTreeRoots[entryTreeRoot]
        treeRoots = ArrayList(treeRoots.map { newTreeRoots[it]!! })
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
