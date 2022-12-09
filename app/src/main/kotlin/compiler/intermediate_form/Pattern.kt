package compiler.intermediate_form

sealed class Pattern {

    // Returns null if the pattern doesn't match provided intermediate form tree
    // If the tree matches, it returns the list of unmatched subtrees and
    // captured values (mainly used to store values in matched leaves)
    // TODO: remove this one and replace with the following ones.
    // TODO (optional) I also suggest to change this very convoluted type: Pair<List<IntermediateFormTreeNode>, Map<String, Any>> to a data class with named fields.
    abstract fun matchValue(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>?
    abstract fun matchUnconditional(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>?
    abstract fun matchConditional(node: IntermediateFormTreeNode, targetLabel: String, invert: Boolean): Pair<List<IntermediateFormTreeNode>, Map<String, Any>>?
}
