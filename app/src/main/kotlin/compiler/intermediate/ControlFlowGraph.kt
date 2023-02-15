package compiler.intermediate

import compiler.utils.Ref

data class ControlFlowGraph(
    val entryTreeRoot: Ref<IFTNode>?,
    val unconditionalLinks: Map<Ref<IFTNode>, Ref<IFTNode>>,
    val conditionalTrueLinks: Map<Ref<IFTNode>, Ref<IFTNode>>,
    val conditionalFalseLinks: Map<Ref<IFTNode>, Ref<IFTNode>>
) {
    val treeRoots: List<Ref<IFTNode>> get() = mutableSetOf<Ref<IFTNode>>().apply {
        fun dfs(node: Ref<IFTNode>?) {
            if (node == null || contains(node))
                return
            add(node)
            dfs(unconditionalLinks[node])
            dfs(conditionalTrueLinks[node])
            dfs(conditionalFalseLinks[node])
        }

        dfs(entryTreeRoot)
    }.toList()

    val finalTreeRoots: List<Pair<Ref<IFTNode>, CFGLinkType>> get() = treeRoots.filter {
        it !in unconditionalLinks && (it !in conditionalTrueLinks || it !in conditionalFalseLinks)
    }.map {
        it to when (it) {
            in conditionalTrueLinks -> CFGLinkType.CONDITIONAL_FALSE
            in conditionalFalseLinks -> CFGLinkType.CONDITIONAL_TRUE
            else -> CFGLinkType.UNCONDITIONAL
        }
    }
}

enum class CFGLinkType {
    UNCONDITIONAL,
    CONDITIONAL_TRUE,
    CONDITIONAL_FALSE
}
