package compiler.common.intermediate_form

import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.referenceHashMapOf
import compiler.intermediate_form.Instruction
import compiler.intermediate_form.InstructionSet
import compiler.intermediate_form.IntermediateFormTreeNode
import compiler.intermediate_form.Pattern
import compiler.intermediate_form.Register

typealias PatternChoices = ReferenceHashMap<IntermediateFormTreeNode, Pattern>

// Assumes that every possible IFTNode has at least one viable covering with the passed instruction set
class DynamicCoveringBuilder(private val instructionSet: InstructionSet) : Covering {
    private fun calculateMinCosts(
        matchPatternToParent: (Pattern) -> Pattern.Result?,
        parent: IntermediateFormTreeNode
    ): PatternChoices {
        val minimalCosts = referenceHashMapOf<IntermediateFormTreeNode, Int>()
        val bestPatterns: PatternChoices = referenceHashMapOf()

        // all IFTNodes aside from parent are treated as values
        // as any conditional/unconditional jumps can only be performed from the root of the tree
        // hence recurrence is only called with matchValue
        // and we pass a custom function to deal with the parent
        fun getMinimalCost(iftNode: IntermediateFormTreeNode): Int {
            if (iftNode in minimalCosts) return minimalCosts[iftNode]!!

            fun calculateCostForPattern(pattern: Pattern): Int {
                val matchedPattern = pattern.matchValue(iftNode)!!
                return matchedPattern.subtrees.sumOf { getMinimalCost(it) } + matchedPattern.cost
            }
            val bestPattern = instructionSet.getInstructionSet().filter { it.matchValue(iftNode) != null }
                .minByOrNull(::calculateCostForPattern)!!

            minimalCosts[iftNode] = calculateCostForPattern(bestPattern)
            bestPatterns[iftNode] = bestPattern
            return minimalCosts[iftNode]!!
        }
        val possiblePatternsToCosts = instructionSet.getInstructionSet()
            .filter { matchPatternToParent(it) != null }
            .associateWith { pattern -> matchPatternToParent(pattern)!!.subtrees.sumOf { getMinimalCost(it) } + matchPatternToParent(pattern)!!.cost }
        bestPatterns[parent] = possiblePatternsToCosts.keys.minByOrNull { possiblePatternsToCosts[it]!! }!!
        return bestPatterns
    }

    private fun buildInstructionListBasedOnPatternChoices(
        matchPatternToParent: (Pattern) -> Pattern.Result?,
        parent: IntermediateFormTreeNode,
        patternChoices: PatternChoices
    ): List<Instruction> {
        // anything but the root is value
        fun getInstructionsForValueNode(
            iftNode: IntermediateFormTreeNode,
            patternChoices: PatternChoices
        ): Pair<List<Instruction>, Register> { // Register is where the value of the node will be stored
            val result = patternChoices[iftNode]!!.matchValue(iftNode)!!
            val (childrenInstructions, childrenOutRegisters) = result.subtrees
                .map { getInstructionsForValueNode(it, patternChoices) }.toList().unzip()
            val outRegister = Register()
            return Pair(
                childrenInstructions.flatten() + result.createInstructions(childrenOutRegisters, outRegister),
                outRegister
            )
        }
        val result = matchPatternToParent(patternChoices[parent]!!)!!
        val (childrenInstructions, childrenOutRegisters) = result.subtrees
            .map { getInstructionsForValueNode(it, patternChoices) }.toList().unzip()
        return childrenInstructions.flatten() + result.createInstructions(childrenOutRegisters, Register())
    }

    private fun coverGeneric(
        matchPatternToParent: (Pattern) -> Pattern.Result?,
        parent: IntermediateFormTreeNode
    ): List<Instruction> {
        val patternChoices = calculateMinCosts(matchPatternToParent, parent)
        return buildInstructionListBasedOnPatternChoices(matchPatternToParent, parent, patternChoices)
    }

    override fun coverUnconditional(iftNode: IntermediateFormTreeNode): List<Instruction> {
        return coverGeneric({ pattern: Pattern -> pattern.matchUnconditional(iftNode) }, iftNode)
    }

    override fun coverConditional(
        iftNode: IntermediateFormTreeNode,
        targetLabel: String,
        invert: Boolean
    ): List<Instruction> {
        return coverGeneric({ pattern -> pattern.matchConditional(iftNode, targetLabel, invert) }, iftNode)
    }
}
