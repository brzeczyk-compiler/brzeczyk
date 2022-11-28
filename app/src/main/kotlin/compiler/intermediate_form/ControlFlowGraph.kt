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
}

enum class CFGLinkType {
    UNCONDITIONAL,
    CONDITIONAL_TRUE,
    CONDITIONAL_FALSE
}
