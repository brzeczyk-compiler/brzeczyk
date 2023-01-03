package compiler.intermediate

import compiler.utils.Ref
import compiler.utils.mutableRefMapOf
import compiler.utils.mutableRefSetOf
import compiler.utils.refMapOf

class IncorrectControlFlowGraphError(message: String) : Exception(message)

class ControlFlowGraphBuilder(@JvmField var entryTreeRoot: IFTNode? = null) {
    private var unconditionalLinks = mutableRefMapOf<IFTNode, IFTNode>()
    private var conditionalTrueLinks = mutableRefMapOf<IFTNode, IFTNode>()
    private var conditionalFalseLinks = mutableRefMapOf<IFTNode, IFTNode>()
    private var treeRoots = mutableListOf<IFTNode>()
    private var treeRootsSet = mutableRefSetOf<IFTNode>()

    init {
        entryTreeRoot?.let {
            treeRoots.add(it)
            treeRootsSet.add(Ref(it))
        }
    }

    fun setEntryTreeRoot(root: IFTNode) {
        if (entryTreeRoot != null)
            throw IncorrectControlFlowGraphError("Tried to create second entryTreeRoot in CFGBuilder")
        entryTreeRoot = root
        if (Ref(root) !in treeRootsSet) {
            treeRoots.add(root)
            treeRootsSet.add(Ref(root))
        }
    }

    fun addLink(from: Pair<IFTNode, CFGLinkType>?, to: IFTNode, addDestination: Boolean = true) {
        if (addDestination && Ref(to) !in treeRootsSet) {
            treeRoots.add(to)
            treeRootsSet.add(Ref(to))
        }
        if (from != null) {
            if (Ref(from.first) !in treeRootsSet) {
                treeRoots.add(from.first)
                treeRootsSet.add(Ref(from.first))
            }

            val links = when (from.second) {
                CFGLinkType.UNCONDITIONAL -> unconditionalLinks
                CFGLinkType.CONDITIONAL_TRUE -> conditionalTrueLinks
                CFGLinkType.CONDITIONAL_FALSE -> conditionalFalseLinks
            }
            links[Ref(from.first)] = Ref(to)
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
        for (treeRoot in cfg.treeRoots) {
            if (Ref(treeRoot) !in treeRootsSet) {
                treeRoots.add(treeRoot)
                treeRootsSet.add(Ref(treeRoot))
            }
        }

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
                listOf(iftNode),
                iftNode, refMapOf(), refMapOf(), refMapOf()
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
