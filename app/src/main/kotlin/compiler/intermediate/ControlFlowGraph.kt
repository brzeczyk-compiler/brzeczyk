package compiler.intermediate

import com.google.common.base.Objects
import compiler.utils.Ref

class ControlFlowGraph(
    val treeRoots: List<IFTNode>,
    val entryTreeRoot: IFTNode?,
    val unconditionalLinks: Map<Ref<IFTNode>, Ref<IFTNode>>,
    val conditionalTrueLinks: Map<Ref<IFTNode>, Ref<IFTNode>>,
    val conditionalFalseLinks: Map<Ref<IFTNode>, Ref<IFTNode>>
) {
    val finalTreeRoots: List<Pair<IFTNode, CFGLinkType>> get() = treeRoots.filter {
        Ref(it) !in unconditionalLinks && (Ref(it) !in conditionalTrueLinks || Ref(it) !in conditionalFalseLinks)
    }.map {
        it to when (Ref(it)) {
            in conditionalTrueLinks -> CFGLinkType.CONDITIONAL_FALSE
            in conditionalFalseLinks -> CFGLinkType.CONDITIONAL_TRUE
            else -> CFGLinkType.UNCONDITIONAL
        }
    }

    override fun equals(other: Any?): Boolean { // TODO: this should be a data class with Refs at treeRoots and entryTreeRoot
        if (other !is ControlFlowGraph)
            return false
        if (other.treeRoots.map(::Ref) != treeRoots.map(::Ref))
            return false
        if (other.entryTreeRoot !== entryTreeRoot)
            return false
        if (other.unconditionalLinks != unconditionalLinks)
            return false
        if (other.conditionalTrueLinks != conditionalTrueLinks)
            return false
        if (other.conditionalFalseLinks != conditionalFalseLinks)
            return false
        return true
    }

    override fun hashCode(): Int = Objects.hashCode(treeRoots.map(::Ref), entryTreeRoot?.let(::Ref), unconditionalLinks, conditionalTrueLinks, conditionalFalseLinks)

    fun isIsomorphicTo(other: ControlFlowGraph): Boolean {
        if (other.treeRoots != treeRoots)
            return false

        val translation = (other.treeRoots.map(::Ref) zip treeRoots.map(::Ref)).toMap()

        fun translateMap(map: Map<Ref<IFTNode>, Ref<IFTNode>>) = map.map { translation[it.key]!! to translation[it.value]!! }.toMap()

        return ControlFlowGraph(
            treeRoots,
            other.entryTreeRoot?.let { translation[Ref(it)]!!.value },
            translateMap(other.unconditionalLinks),
            translateMap(other.conditionalTrueLinks),
            translateMap(other.conditionalFalseLinks)
        ) == this
    }
}

enum class CFGLinkType {
    UNCONDITIONAL,
    CONDITIONAL_TRUE,
    CONDITIONAL_FALSE
}
