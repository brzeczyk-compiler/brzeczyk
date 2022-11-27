package compiler.common.intermediate_form

import compiler.common.reference_collections.combineReferenceMaps
import compiler.common.reference_collections.referenceMapOf
import compiler.intermediate_form.ControlFlowGraph
import compiler.intermediate_form.IntermediateFormTreeNode

fun mergeCFGsUnconditionally(first: ControlFlowGraph?, second: ControlFlowGraph?): ControlFlowGraph? {
    return when {
        first == null -> second
        second == null -> first
        else -> {
            val newLinks = referenceMapOf(first.finalTreeRoots.map { it to second.entryTreeRoot!! })
            val unconditionalLinks = combineReferenceMaps(first.unconditionalLinks, second.unconditionalLinks, newLinks)
            val conditionalTrueLinks = combineReferenceMaps(first.conditionalTrueLinks, second.conditionalTrueLinks)
            val conditionalFalseLinks = combineReferenceMaps(first.conditionalFalseLinks, second.conditionalFalseLinks)
            ControlFlowGraph(first.treeRoots + second.treeRoots, first.entryTreeRoot, unconditionalLinks, conditionalTrueLinks, conditionalFalseLinks)
        }
    }
}

fun mergeCFGsConditionally(condition: ControlFlowGraph, trueBranch: ControlFlowGraph, falseBranch: ControlFlowGraph): ControlFlowGraph {
    val treeRoots = condition.treeRoots + trueBranch.treeRoots + falseBranch.treeRoots
    val unconditionalLinks = combineReferenceMaps(condition.unconditionalLinks, trueBranch.unconditionalLinks, falseBranch.unconditionalLinks)
    val newTrueLinks = referenceMapOf(condition.finalTreeRoots.map { it to trueBranch.entryTreeRoot!! })
    val conditionalTrueLinks = combineReferenceMaps(condition.conditionalTrueLinks, trueBranch.conditionalTrueLinks, falseBranch.conditionalTrueLinks, newTrueLinks)
    val newFalseLinks = referenceMapOf(condition.finalTreeRoots.map { it to falseBranch.entryTreeRoot!! })
    val conditionalFalseLinks = combineReferenceMaps(condition.conditionalFalseLinks, trueBranch.conditionalFalseLinks, falseBranch.conditionalFalseLinks, newFalseLinks)
    return ControlFlowGraph(treeRoots, condition.entryTreeRoot, unconditionalLinks, conditionalTrueLinks, conditionalFalseLinks)
}

fun addTreeToCFG(cfg: ControlFlowGraph?, tree: IntermediateFormTreeNode): ControlFlowGraph {
    val singleTreeCFG = ControlFlowGraph(listOf(tree), tree, referenceMapOf(), referenceMapOf(), referenceMapOf())
    return mergeCFGsUnconditionally(cfg, singleTreeCFG)!!
}
