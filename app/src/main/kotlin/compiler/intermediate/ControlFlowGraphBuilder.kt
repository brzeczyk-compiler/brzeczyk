package compiler.intermediate

import compiler.utils.referenceHashMapOf
import compiler.utils.referenceHashSetOf

class IncorrectControlFlowGraphError(message: String) : Exception(message)

class ControlFlowGraphBuilder(@JvmField var entryTreeRoot: IFTNode? = null) {
    private var unconditionalLinks = referenceHashMapOf<IFTNode, IFTNode>()
    private var conditionalTrueLinks = referenceHashMapOf<IFTNode, IFTNode>()
    private var conditionalFalseLinks = referenceHashMapOf<IFTNode, IFTNode>()
    private var treeRoots = referenceHashSetOf<IFTNode>()

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
        }
        if (entryTreeRoot == null && addDestination) setEntryTreeRoot(to)
    }

    fun addLinksFromAllFinalRoots(linkType: CFGLinkType, to: IFTNode) {
        build().finalTreeRoots.forEach {
            if (it.second == CFGLinkType.UNCONDITIONAL)
                addLink(Pair(it.first, linkType), to)
            else if (linkType == CFGLinkType.UNCONDITIONAL || linkType == it.second)
                addLink(it, to)
            else
                throw IllegalArgumentException()
        }

        if (entryTreeRoot == null)
            setEntryTreeRoot(to)
    }

    fun addAllFrom(cfg: ControlFlowGraph) {
        if (entryTreeRoot == null)
            entryTreeRoot = cfg.entryTreeRoot
        for (treeRoot in cfg.treeRoots)
            if (treeRoot !in treeRoots)
                treeRoots.add(treeRoot)

        unconditionalLinks.putAll(cfg.unconditionalLinks)
        conditionalTrueLinks.putAll(cfg.conditionalTrueLinks)
        conditionalFalseLinks.putAll(cfg.conditionalFalseLinks)
    }

    fun mergeUnconditionally(cfg: ControlFlowGraph): ControlFlowGraphBuilder {
        build().finalTreeRoots.forEach {
            addLink(it, cfg.entryTreeRoot!!, false)
        }
        if (entryTreeRoot == null) entryTreeRoot = cfg.entryTreeRoot
        addAllFrom(cfg)
        return this
    }

    fun mergeConditionally(cfgTrue: ControlFlowGraph, cfgFalse: ControlFlowGraph): ControlFlowGraphBuilder {
        build().finalTreeRoots.forEach {
            if (it.second != CFGLinkType.UNCONDITIONAL)
                throw IllegalArgumentException()
            addLink(Pair(it.first, CFGLinkType.CONDITIONAL_TRUE), cfgTrue.entryTreeRoot!!, false)
            addLink(Pair(it.first, CFGLinkType.CONDITIONAL_FALSE), cfgFalse.entryTreeRoot!!, false)
        }
        addAllFrom(cfgTrue)
        addAllFrom(cfgFalse)
        return this
    }

    fun addSingleTree(iftNode: IFTNode): ControlFlowGraphBuilder {
        mergeUnconditionally(
            ControlFlowGraph(
                referenceHashSetOf(iftNode),
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
