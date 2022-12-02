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
        if (treeRoots.toList() != other.treeRoots.toList()) return false
        if (entryTreeRoot != other.entryTreeRoot) return false
        if (unconditionalLinks.toList() != other.unconditionalLinks.toList()) return false
        if (conditionalTrueLinks.toList() != other.conditionalTrueLinks.toList()) return false
        if (conditionalFalseLinks.toList() != other.conditionalFalseLinks.toList()) return false
        return true
    }
}

enum class CFGLinkType {
    UNCONDITIONAL,
    CONDITIONAL_TRUE,
    CONDITIONAL_FALSE
}
