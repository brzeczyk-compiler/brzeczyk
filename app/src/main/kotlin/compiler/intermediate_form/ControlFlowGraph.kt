package compiler.intermediate_form

import compiler.common.reference_collections.ReferenceMap

typealias IFTNode = IntermediateFormTreeNode

data class ControlFlowGraph(
    val treeRoots: List<IFTNode>,
    val entryTreeRoot: IFTNode?,
    val unconditionalLinks: ReferenceMap<IFTNode, IFTNode>,
    val conditionalTrueLinks: ReferenceMap<IFTNode, IFTNode>,
    val conditionalFalseLinks: ReferenceMap<IFTNode, IFTNode>
) {
    val finalTreeRoots: List<IFTNode> get() = treeRoots.filter {
        it !in unconditionalLinks && it !in conditionalTrueLinks && it !in conditionalFalseLinks
    }

    fun equalsByValue(other: ControlFlowGraph): Boolean {
        if (treeRoots.toSet() != other.treeRoots.toSet()) return false
        if (entryTreeRoot != other.entryTreeRoot) return false
        if (unconditionalLinks.toList().toSet() != other.unconditionalLinks.toList().toSet()) return false
        if (conditionalTrueLinks.toList().toSet() != other.conditionalTrueLinks.toList().toSet()) return false
        if (conditionalFalseLinks.toList().toSet() != other.conditionalFalseLinks.toList().toSet()) return false
        return true
    }
}

enum class CFGLinkType {
    UNCONDITIONAL,
    CONDITIONAL_TRUE,
    CONDITIONAL_FALSE
}
