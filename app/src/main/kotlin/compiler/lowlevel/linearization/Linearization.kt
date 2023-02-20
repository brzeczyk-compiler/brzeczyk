package compiler.lowlevel.linearization

import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.IFTNode
import compiler.lowlevel.Asmable
import compiler.lowlevel.Instruction.UnconditionalJumpInstruction.JmpL
import compiler.lowlevel.Label
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf

class Linearization(private val covering: Covering) {
    fun linearize(cfg: ControlFlowGraph): List<Asmable> {
        if (cfg.entryTreeRoot == null)
            return emptyList()

        val instructions = mutableListOf<Asmable>()
        val labels = mutableKeyRefMapOf<IFTNode, String>()
        val usedLabels = mutableSetOf<String>()

        fun assignLabel(node: IFTNode): String {
            val label = if (node is IFTNode.LabeledNode) node.label else "._" + labels.size
            labels[Ref(node)] = label
            return label
        }

        fun addLabel(label: String) {
            instructions.add(Label(label))
        }

        fun dfs(node: IFTNode, nextLabel: String) {
            addLabel(labels[Ref(node)] ?: assignLabel(node))
            val actualNode =
                if (node is IFTNode.LabeledNode) {
                    usedLabels.add(node.label)
                    node.node
                } else node

            fun addUnconditional() {
                instructions.addAll(covering.coverUnconditional(actualNode))
            }

            fun addConditional(whenTrue: Boolean, targetLabel: String) {
                instructions.addAll(covering.coverConditional(actualNode, targetLabel, !whenTrue))
                usedLabels.add(targetLabel)
            }

            fun addJump(targetLabel: String) {
                if (targetLabel != nextLabel) {
                    instructions.add(JmpL(targetLabel))
                    usedLabels.add(targetLabel)
                }
            }

            if (Ref(node) in cfg.unconditionalLinks) {
                val target = cfg.unconditionalLinks[Ref(node)]!!.value

                if (node !is IFTNode.NoOp)
                    addUnconditional()

                if (Ref(target) in labels)
                    addJump(labels[Ref(target)]!!)
                else
                    dfs(target, nextLabel)
            } else if (Ref(node) in cfg.conditionalTrueLinks && Ref(node) in cfg.conditionalFalseLinks) {
                val targetWhenTrue = cfg.conditionalTrueLinks[Ref(node)]!!.value
                val targetWhenFalse = cfg.conditionalFalseLinks[Ref(node)]!!.value

                if (Ref(targetWhenTrue) in labels && Ref(targetWhenFalse) in labels) {
                    addConditional(true, labels[Ref(targetWhenTrue)]!!)
                    addJump(labels[Ref(targetWhenFalse)]!!)
                } else if (Ref(targetWhenTrue) in labels) {
                    addConditional(true, labels[Ref(targetWhenTrue)]!!)
                    dfs(targetWhenFalse, nextLabel)
                } else if (Ref(targetWhenFalse) in labels) {
                    addConditional(false, labels[Ref(targetWhenFalse)]!!)
                    dfs(targetWhenTrue, nextLabel)
                } else {
                    val label = assignLabel(targetWhenFalse)
                    addConditional(false, label)
                    dfs(targetWhenTrue, label)
                    dfs(targetWhenFalse, nextLabel)
                }
            } else if (Ref(node) in cfg.conditionalTrueLinks || Ref(node) in cfg.conditionalFalseLinks) {
                throw IllegalArgumentException() // unreachable state
            } else {
                addUnconditional() // this must be terminal node, so no jump is needed
            }
        }

        dfs(cfg.entryTreeRoot.value, "")

        return instructions.filter { it !is Label || it.label in usedLabels }
    }
}
