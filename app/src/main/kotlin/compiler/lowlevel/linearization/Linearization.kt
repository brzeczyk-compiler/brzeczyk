package compiler.lowlevel.linearization

import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.IFTNode
import compiler.lowlevel.Asmable
import compiler.lowlevel.Instruction.UnconditionalJumpInstruction.Jmp
import compiler.lowlevel.Label
import compiler.utils.referenceHashMapOf

object Linearization {
    fun linearize(
        cfg: ControlFlowGraph,
        covering: Covering,
    ): List<Asmable> {
        if (cfg.entryTreeRoot == null)
            return emptyList()

        val instructions = mutableListOf<Asmable>()
        val labels = referenceHashMapOf<IFTNode, String>()
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
            println("visit $node $nextLabel")

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
//                println("unconditional")
                val target = cfg.unconditionalLinks[node]!!

                addUnconditional()

                if (target in labels)
                    addJump(labels[target]!!)
                else
                    dfs(target, nextLabel)
            } else if (node in cfg.conditionalTrueLinks && node in cfg.conditionalFalseLinks) {
//                println("conditional")
                val targetWhenTrue = cfg.conditionalTrueLinks[node]!!
                val targetWhenFalse = cfg.conditionalFalseLinks[node]!!

                if (targetWhenTrue in labels && targetWhenFalse in labels) {
                    addConditional(true, labels[targetWhenTrue]!!)
                    addJump(labels[targetWhenFalse]!!)
                } else if (targetWhenTrue in labels) {
                    addConditional(true, labels[targetWhenTrue]!!)
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
            } else if (node in cfg.conditionalTrueLinks || node in cfg.conditionalFalseLinks) {
                throw IllegalArgumentException() // unreachable state
            } else {
//                println("ret")
                addUnconditional() // this must be RET, so no jump is needed
            }
        }

        dfs(cfg.entryTreeRoot, "")

//        cfg.unconditionalLinks.entries.forEach { println(it) }
//        cfg.treeRoots.forEach { println(it) }
//        instructions.forEach { println(it) }
//        println(usedLabels)
//        println()

        return instructions.filter { it !is Label || it.label in usedLabels }
    }
}
