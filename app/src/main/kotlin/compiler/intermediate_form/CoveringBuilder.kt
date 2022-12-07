package compiler.intermediate_form

import compiler.common.intermediate_form.Covering

class CoveringBuilder(override val instructionSet: InstructionSet) : Covering {
    override fun coverUnconditional(iftNode: IntermediateFormTreeNode): List<Instruction> {
        TODO("Not yet implemented")
    }

    override fun coverConditional(
        iftNode: IntermediateFormTreeNode,
        targetLabel: String,
        invert: Boolean
    ): List<Instruction> {
        TODO("Not yet implemented")
    }
}
