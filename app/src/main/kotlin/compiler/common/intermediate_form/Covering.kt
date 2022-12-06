package compiler.common.intermediate_form

import compiler.intermediate_form.Instruction
import compiler.intermediate_form.InstructionSet
import compiler.intermediate_form.IntermediateFormTreeNode

interface Covering {
    val instructionSet: InstructionSet
    fun coverUnconditional(iftNode: IntermediateFormTreeNode): List<Instruction>
    fun coverConditional(iftNode: IntermediateFormTreeNode, targetLabel: String, invert: Boolean): List<Instruction>
}
