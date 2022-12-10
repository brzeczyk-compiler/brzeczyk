package compiler.intermediate_form

import compiler.common.reference_collections.ReferenceSet
import compiler.common.reference_collections.combineReferenceSets
import compiler.common.reference_collections.referenceHashMapOf
import compiler.common.reference_collections.referenceHashSetOf

object Liveness {
    data class LivenessGraphs(
        val interferenceGraph: Map<Register, Set<Register>>,
        val copyGraph: Map<Register, Set<Register>>
    )

    object LivenessDataFlowAnalyser : DataFlowAnalyser<ReferenceSet<Register>>() {
        override val backward = true

        override val entryPointValue: ReferenceSet<Register> = referenceHashSetOf()

        override val latticeMaxElement: ReferenceSet<Register> = referenceHashSetOf()

        override fun latticeMeetFunction(elements: Collection<ReferenceSet<Register>>): ReferenceSet<Register> {
            return combineReferenceSets(elements.toList())
        }

        override fun transferFunction(instruction: Instruction, inValue: ReferenceSet<Register>): ReferenceSet<Register> {
            val gen = instruction.regsUsed
            val kill = instruction.regsDefined
            return combineReferenceSets(referenceHashSetOf(gen.toList()), referenceHashSetOf(inValue.filter { it !in kill }))
        }
    }

    fun computeLiveness(linearProgram: List<Asmable>): LivenessGraphs {
        val instructionList = linearProgram.filterIsInstance<Instruction>()

        // find all registers, prepare empty graphs
        val allRegisters = referenceHashSetOf<Register>()
        instructionList.forEach {
            allRegisters.addAll(it.regsDefined)
            allRegisters.addAll(it.regsUsed)
        }

        val interferenceGraph = referenceHashMapOf(allRegisters.map { it to referenceHashSetOf<Register>() })
        val copyGraph = referenceHashMapOf(allRegisters.map { it to referenceHashSetOf<Register>() })

        // run analysis and fill the graphs
        val dataFlowResult = LivenessDataFlowAnalyser.analyse(linearProgram)
        val outLiveRegisters = dataFlowResult.outValues.mapValues { (instr, regs) -> combineReferenceSets(regs, referenceHashSetOf(instr.regsDefined.toList())) }

        instructionList.forEach {
            for (definedReg in it.regsDefined)
                for (regLiveOnOutput in outLiveRegisters[it]!!)
                    if (definedReg !== regLiveOnOutput) {
                        if (it is Instruction.InPlaceInstruction.MoveRR && it.reg_dest == definedReg && it.reg_src == regLiveOnOutput) {
                            copyGraph[it.reg_dest]!!.add(it.reg_src)
                            copyGraph[it.reg_src]!!.add(it.reg_dest)
                        } else {
                            interferenceGraph[definedReg]!!.add(regLiveOnOutput)
                            interferenceGraph[regLiveOnOutput]!!.add(definedReg)
                        }
                    }
        }

        return LivenessGraphs(interferenceGraph, copyGraph)
    }
}
