package compiler.lowlevel.linearization

import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import compiler.lowlevel.Instruction
import compiler.utils.ReferenceHashMap
import compiler.utils.referenceHashMapOf

typealias PatternChoices = ReferenceHashMap<IFTNode, Pattern>
val matchValue: (Pattern, IFTNode) -> Pattern.Result? = { pattern, iftnode -> pattern.matchValue(iftnode) }

// Assumes that every possible IFTNode has at least one viable covering with the passed instruction set
class DynamicCoveringBuilder(private val instructionSet: List<Pattern>) : Covering {
    private fun getBestPatterns(
        topPatternMatcher: (Pattern, IFTNode) -> Pattern.Result?,
        parent: IFTNode
    ): PatternChoices {
        val minimalCosts = referenceHashMapOf<IFTNode, Int>()
        val bestPatterns: PatternChoices = referenceHashMapOf()

        // all IFTNodes aside from parent are treated as values
        // as any conditional/unconditional jumps can only be performed from the root of the tree
        // hence the default is matchValue, which will be in used calls aside from the top one
        // and we pass a custom function to deal with the parent
        fun calculateBestPatternAndCost(
            iftNode: IFTNode,
            patternMatcher: (Pattern, IFTNode) -> Pattern.Result? = matchValue
        ): Int {
            if (iftNode in minimalCosts) return minimalCosts[iftNode]!!

            fun calculateCostForPattern(pattern: Pattern): Int? {
                val matchedPattern = patternMatcher(pattern, iftNode)
                if (matchedPattern == null) return null
                return matchedPattern.subtrees.sumOf { calculateBestPatternAndCost(it) } + matchedPattern.cost
            }
            val possiblePatternsToCosts = instructionSet.associateWith(::calculateCostForPattern)
                .filter { it.value != null }
            minimalCosts[iftNode] = possiblePatternsToCosts.maxOf { it.value as Int }
            bestPatterns[iftNode] = possiblePatternsToCosts.minByOrNull { it.value!! }!!.key
            return minimalCosts[iftNode]!!
        }
        calculateBestPatternAndCost(parent, topPatternMatcher)
        return bestPatterns
    }

    private fun buildInstructionListBasedOnPatternChoices(
        topPatternMatcher: (Pattern, IFTNode) -> Pattern.Result?,
        parent: IFTNode,
        patternChoices: PatternChoices
    ): List<Instruction> {
        // anything but the root is value
        fun getInstructionsForValueNode(
            iftNode: IFTNode,
            patternMatcher: (Pattern, IFTNode) -> Pattern.Result? = matchValue,
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
        topPatternMatcher: (Pattern, IFTNode) -> Pattern.Result?,
        parent: IFTNode
    ): List<Instruction> {
        val patternChoices = getBestPatterns(topPatternMatcher, parent)
        return buildInstructionListBasedOnPatternChoices(topPatternMatcher, parent, patternChoices)
    }

    override fun coverUnconditional(iftNode: IFTNode): List<Instruction> {
        return coverGeneric({ pattern, node -> pattern.matchUnconditional(node) }, iftNode)
    }

    override fun coverConditional(
        iftNode: IFTNode,
        targetLabel: String,
        invert: Boolean
    ): List<Instruction> {
        return coverGeneric({ pattern, node -> pattern.matchConditional(node, targetLabel, invert) }, iftNode)
    }
}
