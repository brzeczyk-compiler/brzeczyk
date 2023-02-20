package compiler.intermediate

import compiler.utils.Ref
import compiler.utils.mutableRefMapOf
import compiler.utils.mutableRefSetOf

class IncorrectControlFlowGraphError(message: String) : Exception(message)

class ControlFlowGraphBuilder(private var entryTreeRoot: Ref<IFTNode>? = null) {
    private val unconditionalLinks = mutableRefMapOf<IFTNode, IFTNode>()
    private val conditionalTrueLinks = mutableRefMapOf<IFTNode, IFTNode>()
    private val conditionalFalseLinks = mutableRefMapOf<IFTNode, IFTNode>()
    private val treeRoots = mutableRefSetOf<IFTNode>()

    constructor(entryTreeRoot: IFTNode?) : this(entryTreeRoot?.let(::Ref))

    init {
        entryTreeRoot?.let {
            treeRoots.add(it)
        }
    }

    private fun setEntryTreeRoot(root: Ref<IFTNode>) {
        if (entryTreeRoot != null)
            throw IncorrectControlFlowGraphError("Tried to create second entryTreeRoot in CFGBuilder")
        entryTreeRoot = root
        treeRoots.add(root)
    }

    fun addLink(from: Pair<Ref<IFTNode>, CFGLinkType>?, to: Ref<IFTNode>, addDestination: Boolean = true) {
        if (addDestination)
            treeRoots.add(to)

        if (from != null) {
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

    fun addLinksFromAllFinalRoots(linkType: CFGLinkType, to: Ref<IFTNode>) {
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

    fun addLinksFromAllFinalRoots(linkType: CFGLinkType, to: IFTNode) = addLinksFromAllFinalRoots(linkType, Ref(to))

    fun addAllFrom(cfg: ControlFlowGraph, setEntryPoint: Boolean = true): ControlFlowGraphBuilder {
        if (entryTreeRoot == null && setEntryPoint)
            entryTreeRoot = cfg.entryTreeRoot
        for (treeRoot in cfg.treeRoots)
            treeRoots.add(treeRoot)

        unconditionalLinks.putAll(cfg.unconditionalLinks)
        conditionalTrueLinks.putAll(cfg.conditionalTrueLinks)
        conditionalFalseLinks.putAll(cfg.conditionalFalseLinks)
        return this
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
        mergeUnconditionally(ControlFlowGraph(Ref(iftNode), emptyMap(), emptyMap(), emptyMap()))
        return this
    }

    fun build(): ControlFlowGraph {
        return ControlFlowGraph(
            entryTreeRoot,
            unconditionalLinks,
            conditionalTrueLinks,
            conditionalFalseLinks
        )
    }

    val isEmpty: Boolean get() = entryTreeRoot == null
}
