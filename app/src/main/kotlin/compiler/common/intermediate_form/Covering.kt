package compiler.common.intermediate_form

import compiler.intermediate_form.Instruction
import compiler.intermediate_form.IntermediateFormTreeNode

interface Covering {
    fun coverUnconditional(iftNode: IntermediateFormTreeNode): List<Instruction>
    fun coverConditional(iftNode: IntermediateFormTreeNode, targetLabel: String, invert: Boolean): List<Instruction>
}
