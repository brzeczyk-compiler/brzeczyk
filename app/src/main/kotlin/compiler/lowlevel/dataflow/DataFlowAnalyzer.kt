package compiler.lowlevel.dataflow

import compiler.lowlevel.Asmable
import compiler.lowlevel.Instruction
import compiler.lowlevel.Label
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf
import compiler.utils.refSetOf

abstract class DataFlowAnalyzer<SL> {
    abstract val backward: Boolean

    abstract val entryPointValue: SL

    abstract val latticeMaxElement: SL

    abstract fun latticeMeetFunction(elements: Collection<SL>): SL

    abstract fun transferFunction(instruction: Instruction, inValue: SL): SL

    private fun getPredecessors(linearProgram: List<Asmable>): Map<Ref<Instruction>, List<Instruction>> {
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
        val predecessors = mutableKeyRefMapOf<Instruction, MutableList<Instruction>>()
        instructionList.forEach { predecessors[Ref(it)] = mutableListOf() }
        fun addPredecessor(from: Instruction, to: Instruction) {
            if (backward) predecessors[Ref(from)]!!.add(to)
            else predecessors[Ref(to)]!!.add(from)
        }

        instructionList.forEachIndexed { index, it ->
            when (it) {
                is Instruction.ConditionalJumpInstruction -> {
                    addPredecessor(it, labelToInstruction[it.targetLabel]!!)
                    addPredecessor(it, instructionList[index + 1])
                }

                is Instruction.UnconditionalJumpInstruction ->
                    addPredecessor(it, labelToInstruction[it.targetLabel]!!)

                is Instruction.TerminalInstruction -> {}

                is Instruction.InPlaceInstruction ->
                    addPredecessor(it, instructionList[index + 1])
            }
        }

        return predecessors.map { it.toPair() }.toMap()
    }

    private fun getEntryPoints(instructionList: List<Instruction>): Set<Ref<Instruction>> {
        return if (backward)
            instructionList.filterIsInstance<Instruction.TerminalInstruction>().map(::Ref).toSet()
        else
            refSetOf(instructionList.first())
    }

    data class DataFlowResult<SL>(val inValues: Map<Ref<Instruction>, SL>, val outValues: Map<Ref<Instruction>, SL>)

    fun analyze(linearProgram: List<Asmable>): DataFlowResult<SL> {
        val predecessors = getPredecessors(linearProgram)
        val instructionList = linearProgram.filterIsInstance<Instruction>()
        val entryPoints = getEntryPoints(instructionList)

        val inStates = mutableKeyRefMapOf<Instruction, SL>() // actually output states for backward mode
        val outStates = mutableKeyRefMapOf<Instruction, SL>() // actually input states for backward mode

        // initialize input states
        instructionList.forEach {
            inStates[Ref(it)] = if (Ref(it) in entryPoints) entryPointValue else latticeMaxElement
        }

        // iterate until you reach fixed point
        while (true) {
            var inStatesChanged = false

            // apply transfer function
            instructionList.forEach {
                outStates[Ref(it)] = transferFunction(it, inStates[Ref(it)]!!)
            }

            // collect outputs into inputs
            instructionList.filter { predecessors[Ref(it)]!!.isNotEmpty() }.forEach {
                val newInState = latticeMeetFunction(predecessors[Ref(it)]!!.map { predecessor -> outStates[Ref(predecessor)]!! })
                if (newInState != inStates[Ref(it)]!!)
                    inStatesChanged = true
                inStates[Ref(it)] = newInState
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
