package compiler.intermediate_form

import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.referenceHashMapOf

class IncorrectControlFlowGraphError(message: String) : Exception(message)

class ControlFlowGraphBuilder(@JvmField var entryTreeRoot: IFTNode? = null) {
    private var unconditionalLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private var conditionalTrueLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private var conditionalFalseLinks = ReferenceHashMap<IFTNode, IFTNode>()
    private var treeRoots = ArrayList<IFTNode>()
    private val finalTreeRoots: List<IFTNode> get() = treeRoots.filter {
        it !in unconditionalLinks && it !in conditionalTrueLinks && it !in conditionalFalseLinks
    }

    init {
        entryTreeRoot?.let { treeRoots.add(it) }
    }

    fun setEntryTreeRoot(root: IFTNode) {
        if (entryTreeRoot != null)
            throw IncorrectControlFlowGraphError("Tried to create second entryTreeRoot in CFGBuilder")
        entryTreeRoot = root
        if (!treeRoots.contains(root))
            treeRoots.add(root)
    }

    fun addLink(from: Pair<IFTNode, CFGLinkType>?, to: IFTNode, addDestination: Boolean = true) {
        if (addDestination && !treeRoots.contains(to))
            treeRoots.add(to)
        if (from != null) {
            if (!treeRoots.contains(from.first))
                treeRoots.add(from.first)

            val links = when (from.second) {
                CFGLinkType.UNCONDITIONAL -> unconditionalLinks
                CFGLinkType.CONDITIONAL_TRUE -> conditionalTrueLinks
                CFGLinkType.CONDITIONAL_FALSE -> conditionalFalseLinks
            }
            links[from.first] = to
        } else if (entryTreeRoot == null) setEntryTreeRoot(to)
    }

    fun addLinksFromAllFinalRoots(linkType: CFGLinkType, to: IFTNode) {
        val linksToAdd = finalTreeRoots.map { Pair(Pair(it, linkType), to) }.toList()
        for (link in linksToAdd)
            addLink(link.first, link.second)
        if (entryTreeRoot == null)
            setEntryTreeRoot(to)
    }

    fun addAllFrom(cfg: ControlFlowGraph, modifyEntryTreeRoot: Boolean = false) {
        treeRoots.addAll(cfg.treeRoots)

        if (modifyEntryTreeRoot || entryTreeRoot == null)
            entryTreeRoot = cfg.entryTreeRoot

        unconditionalLinks.putAll(cfg.unconditionalLinks)
        conditionalTrueLinks.putAll(cfg.conditionalTrueLinks)
        conditionalFalseLinks.putAll(cfg.conditionalFalseLinks)
    }

    fun mergeUnconditionally(cfg: ControlFlowGraph): ControlFlowGraphBuilder {
        build().finalTreeRoots.forEach {
            addLink(Pair(it, CFGLinkType.UNCONDITIONAL), cfg.entryTreeRoot!!, false)
        }
        addAllFrom(cfg)
        if (entryTreeRoot == null) entryTreeRoot = cfg.entryTreeRoot
        return this
    }

    fun mergeConditionally(cfgTrue: ControlFlowGraph, cfgFalse: ControlFlowGraph): ControlFlowGraphBuilder {
        build().finalTreeRoots.forEach {
            addLink(Pair(it, CFGLinkType.CONDITIONAL_TRUE), cfgTrue.entryTreeRoot!!, false)
            addLink(Pair(it, CFGLinkType.CONDITIONAL_FALSE), cfgFalse.entryTreeRoot!!, false)
        }
        addAllFrom(cfgTrue)
        addAllFrom(cfgFalse)
        return this
    }

    fun addSingleTree(iftNode: IntermediateFormTreeNode): ControlFlowGraphBuilder {
        mergeUnconditionally(
            ControlFlowGraph(
                listOf(iftNode),
                iftNode, referenceHashMapOf(), referenceHashMapOf(), referenceHashMapOf()
            )
        )
        return this
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
