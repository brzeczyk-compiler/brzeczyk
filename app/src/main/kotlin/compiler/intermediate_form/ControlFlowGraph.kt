package compiler.intermediate_form

import compiler.common.semantic_analysis.ReferenceMap

typealias Node = IntermediateFormTreeNode

class ControlFlowGraph(
    private val treeRoots: List<Node>,
    private val entryTreeRoot: Node,
    private val unconditionalLinks: ReferenceMap<Node, Node>,
    private val conditionalTrueLinks: ReferenceMap<Node, Node>,
    private val conditionalFalseLinks: ReferenceMap<Node, Node>
) {
    // TODO() possibly some traversal here, otherwise make a data class
}
