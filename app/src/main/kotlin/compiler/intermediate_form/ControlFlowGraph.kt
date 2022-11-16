package compiler.intermediate_form

import compiler.common.reference_collections.ReferenceMap

typealias IFTNode = IntermediateFormTreeNode

class ControlFlowGraph(
    private val treeRoots: List<IFTNode>,
    private val entryTreeRoot: IFTNode,
    private val unconditionalLinks: ReferenceMap<IFTNode, IFTNode>,
    private val conditionalTrueLinks: ReferenceMap<IFTNode, IFTNode>,
    private val conditionalFalseLinks: ReferenceMap<IFTNode, IFTNode>
) {
    // TODO() possibly some traversal here, otherwise make a data class
}
