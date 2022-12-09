package compiler.common.intermediate_form

import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.referenceHashMapOf
import compiler.intermediate_form.Instruction
import compiler.intermediate_form.InstructionSet
import compiler.intermediate_form.InstructionSet.InstructionPattern
import compiler.intermediate_form.IntermediateFormTreeNode
import compiler.intermediate_form.Register

typealias MatchResult = Pair<List<IntermediateFormTreeNode>, (List<Register>, Register) -> List<Instruction>>?
typealias PatternChoices = ReferenceHashMap<IntermediateFormTreeNode, InstructionPattern>

// Assumes that every possible IFTNode has at least one viable covering with the passed instruction set
class DynamicCoveringBuilder(private val instructionSet: InstructionSet) : Covering {
    private fun calculateMinCosts(
        matchPatternToParent: (InstructionPattern) -> MatchResult,
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

            fun calculateCostForPattern(pattern: InstructionPattern): Int {
                return pattern.matchValue(iftNode)!!.first.sumOf { getMinimalCost(it) } + pattern.getCost()
            }
            val bestPattern = instructionSet.getInstructionSet().filter { it.matchValue(iftNode) != null }
                .minByOrNull(::calculateCostForPattern)!!

            minimalCosts[iftNode] = calculateCostForPattern(bestPattern)
            bestPatterns[iftNode] = bestPattern
            return minimalCosts[iftNode]!!
        }
        val possiblePatternsToCosts = instructionSet.getInstructionSet()
            .filter { matchPatternToParent(it) != null }
            .associateWith { pattern -> matchPatternToParent(pattern)!!.first.sumOf { getMinimalCost(it) } + pattern.getCost() }
        bestPatterns[parent] = possiblePatternsToCosts.keys.minByOrNull { possiblePatternsToCosts[it]!! }!!
        return bestPatterns
    }

    private fun buildInstructionListBasedOnPatternChoices(
        matchPatternToParent: (InstructionPattern) -> MatchResult,
        parent: IntermediateFormTreeNode,
        patternChoices: PatternChoices
    ): List<Instruction> {
        // anything but the root is value
        fun getInstructionsForValueNode(
            iftNode: IntermediateFormTreeNode,
            patternChoices: PatternChoices
        ): Pair<List<Instruction>, Register> { // Register is where the value of the node will be stored
            val (remainingTrees, instructionConstructor) = patternChoices[iftNode]!!.matchValue(iftNode)!!
            val (childrenInstructions, childrenOutRegisters) = remainingTrees
                .map { getInstructionsForValueNode(it, patternChoices) }.toList().unzip()
            val outRegister = Register()
            return Pair(
                childrenInstructions.flatten() + instructionConstructor(childrenOutRegisters, outRegister),
                outRegister
            )
        }
        val (valueChildren, instructionConstructor) = matchPatternToParent(patternChoices[parent]!!)!!
        val (childrenInstructions, childrenOutRegisters) = valueChildren
            .map { getInstructionsForValueNode(it, patternChoices) }.toList().unzip()
        return childrenInstructions.flatten() + instructionConstructor(childrenOutRegisters, Register())
    }

    private fun coverGeneric(matchPatternToParent: (InstructionPattern) -> MatchResult, parent: IntermediateFormTreeNode): List<Instruction> {
        val patternChoices = calculateMinCosts(matchPatternToParent, parent)
        return buildInstructionListBasedOnPatternChoices(matchPatternToParent, parent, patternChoices)
    }

    override fun coverUnconditional(iftNode: IntermediateFormTreeNode): List<Instruction> {
        return coverGeneric({ pattern: InstructionPattern -> pattern.matchUnconditional(iftNode) }, iftNode)
    }

    override fun coverConditional(
        iftNode: IntermediateFormTreeNode,
        targetLabel: String,
        invert: Boolean
    ): List<Instruction> {
        return coverGeneric({ pattern -> pattern.matchConditional(iftNode, targetLabel, invert) }, iftNode)
    }
}
