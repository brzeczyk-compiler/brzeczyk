package compiler.intermediate_form

import compiler.common.intermediate_form.Covering
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.referenceHashMapOf

typealias PatternChoices = ReferenceHashMap<IntermediateFormTreeNode, Pattern>
val matchValue: (Pattern, IntermediateFormTreeNode) -> Pattern.Result? = { pattern, iftnode -> pattern.matchValue(iftnode) }

// Assumes that every possible IFTNode has at least one viable covering with the passed instruction set
class DynamicCoveringBuilder(private val instructionSet: InstructionSet) : Covering {
    private fun getBestPatterns(
        topPatternMatcher: (Pattern, IntermediateFormTreeNode) -> Pattern.Result?,
        parent: IntermediateFormTreeNode
    ): PatternChoices {
        val minimalCosts = referenceHashMapOf<IntermediateFormTreeNode, Int>()
        val bestPatterns: PatternChoices = referenceHashMapOf()

        // all IFTNodes aside from parent are treated as values
        // as any conditional/unconditional jumps can only be performed from the root of the tree
        // hence the default is matchValue, which will be in used calls aside from the top one
        // and we pass a custom function to deal with the parent
        fun calculateBestPatternAndCost(
            iftNode: IntermediateFormTreeNode,
            patternMatcher: (Pattern, IntermediateFormTreeNode) -> Pattern.Result? = matchValue
        ): Int {
            if (iftNode in minimalCosts) return minimalCosts[iftNode]!!

            fun calculateCostForPattern(pattern: Pattern): Int? {
                val matchedPattern = patternMatcher(pattern, iftNode)
                if (matchedPattern == null) return null
                return matchedPattern.subtrees.sumOf { calculateBestPatternAndCost(it) } + matchedPattern.cost
            }
            val possiblePatternsToCosts = instructionSet.getInstructionSet().associateWith(::calculateCostForPattern)
                .filter { it.value != null }
            minimalCosts[iftNode] = possiblePatternsToCosts.maxOf { it.value as Int }
            bestPatterns[iftNode] = possiblePatternsToCosts.minByOrNull { it.value!! }!!.key
            return minimalCosts[iftNode]!!
        }
        calculateBestPatternAndCost(parent, topPatternMatcher)
        return bestPatterns
    }

    private fun buildInstructionListBasedOnPatternChoices(
        topPatternMatcher: (Pattern, IntermediateFormTreeNode) -> Pattern.Result?,
        parent: IntermediateFormTreeNode,
        patternChoices: PatternChoices
    ): List<Instruction> {
        // anything but the root is value
        fun getInstructionsForValueNode(
            iftNode: IntermediateFormTreeNode,
            patternMatcher: (Pattern, IntermediateFormTreeNode) -> Pattern.Result? = matchValue,
        ): Pair<List<Instruction>, Register> { // Register is where the value of the node will be stored
            val result = patternMatcher(patternChoices[iftNode]!!, iftNode)!!
            val (childrenInstructions, childrenOutRegisters) = result.subtrees
                .map { getInstructionsForValueNode(it) }.toList().unzip()
            val outRegister = Register()
            return Pair(
                childrenInstructions.flatten() + result.createInstructions(childrenOutRegisters, outRegister),
                outRegister
            )
        }
        return getInstructionsForValueNode(parent, topPatternMatcher).first // ignore out register for roots
    }

    private fun coverGeneric(
        topPatternMatcher: (Pattern, IntermediateFormTreeNode) -> Pattern.Result?,
        parent: IntermediateFormTreeNode
    ): List<Instruction> {
        val patternChoices = getBestPatterns(topPatternMatcher, parent)
        return buildInstructionListBasedOnPatternChoices(topPatternMatcher, parent, patternChoices)
    }

    override fun coverUnconditional(iftNode: IntermediateFormTreeNode): List<Instruction> {
        return coverGeneric({ pattern, node -> pattern.matchUnconditional(node) }, iftNode)
    }

    override fun coverConditional(
        iftNode: IntermediateFormTreeNode,
        targetLabel: String,
        invert: Boolean
    ): List<Instruction> {
        return coverGeneric({ pattern, node -> pattern.matchConditional(node, targetLabel, invert) }, iftNode)
    }
}
