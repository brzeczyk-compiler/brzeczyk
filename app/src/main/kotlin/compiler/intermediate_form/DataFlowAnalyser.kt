package compiler.intermediate_form

import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.ReferenceSet
import compiler.common.reference_collections.referenceHashMapOf
import compiler.common.reference_collections.referenceHashSetOf
import compiler.common.reference_collections.referenceMapOf

abstract class DataFlowAnalyser<SL> {
    abstract val backward: Boolean

    abstract val entryPointValue: SL

    abstract val latticeMaxElement: SL

    abstract fun latticeMeetFunction(elements: Collection<SL>): SL

    abstract fun transferFunction(instruction: Instruction, inValue: SL): SL

    private fun getPredecessors(linearProgram: List<Asmable>): ReferenceMap<Instruction, List<Instruction>> {
        val instructionList = linearProgram.filterIsInstance<Instruction>()

        // calculate label to instruction translation
        val labelToInstruction = mutableMapOf<String, Instruction>()
        for ((index, asmable) in linearProgram.withIndex().reversed())
            if (asmable is Label)
                labelToInstruction[asmable.label] = linearProgram[index + 1].let {
                    when (it) {
                        is Label -> labelToInstruction[it.label]!!
                        is Instruction -> it
                    }
                }

        // calculate the predecessors map (forward direction)
        val predecessors = referenceHashMapOf<Instruction, MutableList<Instruction>>()
        instructionList.forEach { predecessors[it] = mutableListOf() }
        fun addPredecessor(from: Instruction, to: Instruction) {
            if (backward) predecessors[from]!!.add(to)
            else predecessors[to]!!.add(from)
        }

        instructionList.forEachIndexed { index, it ->
            when (it) {
                is Instruction.ConditionalJumpInstruction -> {
                    addPredecessor(it, labelToInstruction[it.targetLabel]!!)
                    addPredecessor(it, instructionList[index + 1])
                }

                is Instruction.UnconditionalJumpInstruction ->
                    addPredecessor(it, labelToInstruction[it.targetLabel]!!)

                is Instruction.RetInstruction -> {}

                is Instruction.InPlaceInstruction ->
                    addPredecessor(it, instructionList[index + 1])
            }
        }

        return referenceMapOf(predecessors.map { it.toPair() })
    }

    private fun getEntryPoints(instructionList: List<Instruction>): ReferenceSet<Instruction> {
        return if (backward)
            referenceHashSetOf(instructionList.filterIsInstance<Instruction.RetInstruction>())
        else
            referenceHashSetOf(instructionList.first())
    }

    data class DataFlowResult<SL>(val inValues: ReferenceMap<Instruction, SL>, val outValues: ReferenceMap<Instruction, SL>)

    fun analyse(linearProgram: List<Asmable>): DataFlowResult<SL> {
        val predecessors = getPredecessors(linearProgram)
        val instructionList = linearProgram.filterIsInstance<Instruction>()
        val entryPoints = getEntryPoints(instructionList)

        val inStates = referenceHashMapOf<Instruction, SL>() // actually output states for backward mode
        val outStates = referenceHashMapOf<Instruction, SL>() // actually input states for backward mode

        // initialize input states
        instructionList.forEach {
            inStates[it] = if (it in entryPoints) entryPointValue else latticeMaxElement
        }

        // iterate until you reach fixed point
        while (true) {
            var inStatesChanged = false

            // apply transfer function
            instructionList.forEach {
                outStates[it] = transferFunction(it, inStates[it]!!)
            }

            // collect outputs into inputs
            instructionList.filter { predecessors[it]!!.isNotEmpty() }.forEach {
                val newInState = latticeMeetFunction(predecessors[it]!!.map { predecessor -> outStates[predecessor]!! })
                if (newInState != inStates[it]!!)
                    inStatesChanged = true
                inStates[it] = newInState
            }

            if (!inStatesChanged)
                break
        }

        return if (backward)
            DataFlowResult(outStates, inStates)
        else
            DataFlowResult(inStates, outStates)
    }
}
