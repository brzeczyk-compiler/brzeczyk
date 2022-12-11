package compiler.intermediate_form

// Represents a possible covering of an intermediate form tree with instructions
interface Pattern {
    // Return type for match methods
    // Contains uncovered subtrees, the cost of using this pattern and function that,
    // given list of registers containing results of evaluating subtrees and an output register,
    // returns list of instructions that implement the matched intermediate form tree
    data class Result(
        val subtrees: List<IntermediateFormTreeNode>,
        val cost: Int = 1,
        val createInstructions: (List<Register>, Register) -> List<Instruction>
    )

    fun matchValue(node: IntermediateFormTreeNode): Result? = null
    fun matchUnconditional(node: IntermediateFormTreeNode): Result? = null
    fun matchConditional(node: IntermediateFormTreeNode, targetLabel: String, invert: Boolean): Result? = null
}
