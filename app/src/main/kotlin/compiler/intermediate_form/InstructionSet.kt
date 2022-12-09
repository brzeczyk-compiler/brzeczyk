package compiler.intermediate_form

object InstructionSet {
    data class InstructionPattern(
        val pattern: Pattern,
        val createInstructions: (List<Register>, Register, Map<String, Any>) -> List<Instruction>
    ) {
        fun matchValue(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, (List<Register>, Register) -> List<Instruction>>? = null
        fun matchUnconditional(node: IntermediateFormTreeNode): Pair<List<IntermediateFormTreeNode>, (List<Register>, Register) -> List<Instruction>>? = null
        fun matchConditional(node: IntermediateFormTreeNode, targetLabel: String, invert: Boolean): Pair<List<IntermediateFormTreeNode>, (List<Register>, Register) -> List<Instruction>>? = null
        // TODO find better values
        fun getCost(): Int = 1
    }

    fun getInstructionSet(): List<InstructionPattern> {
        return listOf()
    }
}
