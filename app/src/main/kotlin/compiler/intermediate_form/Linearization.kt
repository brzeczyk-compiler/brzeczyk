package compiler.intermediate_form

import compiler.common.intermediate_form.Covering
import compiler.common.reference_collections.referenceHashMapOf
import compiler.intermediate_form.Instruction.UnconditionalJumpInstruction.Jmp

object Linearization {
    fun linearize(
        cfg: ControlFlowGraph,
        covering: Covering,
    ): List<Asmable> {
        if (cfg.entryTreeRoot == null)
            return emptyList()

        val instructions = mutableListOf<Asmable>()
        val labels = referenceHashMapOf<IFTNode, String>()
        val endLabel = "end"
        val usedLabels = mutableSetOf<String>()

        fun assignLabel(node: IFTNode): String {
            val label = "_" + labels.size
            labels[node] = label
            return label
        }

        fun addLabel(label: String) {
            instructions.add(Label(label))
        }

        fun dfs(node: IFTNode, nextLabel: String) {
            addLabel(labels[node] ?: assignLabel(node))

            fun addUnconditional() {
                instructions.addAll(covering.coverUnconditional(node))
            }

            fun addConditional(whenTrue: Boolean, targetLabel: String) {
                instructions.addAll(covering.coverConditional(node, targetLabel, !whenTrue))
                usedLabels.add(targetLabel)
            }

            fun addJump(targetLabel: String) {
                if (targetLabel != nextLabel) {
                    instructions.add(Jmp(targetLabel))
                    usedLabels.add(targetLabel)
                }
            }

            if (node in cfg.unconditionalLinks) {
                val target = cfg.unconditionalLinks[node]!!

                addUnconditional()

                if (target in labels)
                    addJump(labels[target]!!)
                else
                    dfs(target, nextLabel)
            } else if (node in cfg.conditionalTrueLinks && node in cfg.conditionalFalseLinks) {
                val targetWhenTrue = cfg.conditionalTrueLinks[node]!!
                val targetWhenFalse = cfg.conditionalFalseLinks[node]!!

                if (targetWhenTrue in labels) {
                    addConditional(true, labels[targetWhenTrue]!!)

                    if (targetWhenFalse in labels)
                        addJump(labels[targetWhenFalse]!!)
                    else
                        dfs(targetWhenFalse, nextLabel)
                } else if (targetWhenFalse in labels) {
                    addConditional(false, labels[targetWhenFalse]!!)
                    dfs(targetWhenTrue, nextLabel)
                } else {
                    val label = assignLabel(targetWhenFalse)

                    addConditional(false, label)

                    dfs(targetWhenTrue, label)
                    dfs(targetWhenFalse, nextLabel)
                }
            } else if (node in cfg.conditionalTrueLinks) {
                val target = cfg.conditionalTrueLinks[node]!!

                if (target in labels) {
                    addConditional(true, labels[target]!!)
                    addJump(endLabel)
                } else {
                    addConditional(false, endLabel)
                    dfs(target, nextLabel)
                }
            } else if (node in cfg.conditionalFalseLinks) {
                val target = cfg.conditionalFalseLinks[node]!!

                if (target in labels) {
                    addConditional(false, labels[target]!!)
                    addJump(endLabel)
                } else {
                    addConditional(true, endLabel)
                    dfs(target, nextLabel)
                }
            } else {
                addUnconditional()
                addJump(endLabel)
            }
        }

        dfs(cfg.entryTreeRoot, endLabel)
        addLabel(endLabel)

        return instructions.filter { it !is Label || it.label in usedLabels }
    }
}
