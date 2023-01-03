package compiler.lowlevel.dataflow

import compiler.intermediate.Register
import compiler.lowlevel.Asmable
import compiler.lowlevel.Instruction
import compiler.utils.Ref

object Liveness {
    data class LivenessGraphs(
        val interferenceGraph: Map<Register, Set<Register>>,
        val copyGraph: Map<Register, Set<Register>>,
    )

    fun inducedSubgraph(graph: Map<Register, Set<Register>>, subset: Set<Register>): Map<Register, Set<Register>> =
        graph.entries
            .filter { it.key in subset }
            .associate { it.key to it.value.filter { vertex -> vertex in subset }.toSet() }.toMap()

    object LivenessDataFlowAnalyzer : DataFlowAnalyzer<Set<Register>>() {
        override val backward = true

        override val entryPointValue: Set<Register> = setOf()

        override val latticeMaxElement: Set<Register> = setOf()

        override fun latticeMeetFunction(elements: Collection<Set<Register>>): Set<Register> {
            return elements.fold(emptySet(), Set<Register>::plus)
        }

        override fun transferFunction(instruction: Instruction, inValue: Set<Register>): Set<Register> {
            val gen = instruction.regsUsed
            val kill = instruction.regsDefined
            return gen.toSet() + inValue.filter { it !in kill }.toSet()
        }
    }

    fun computeLiveness(linearProgram: List<Asmable>): LivenessGraphs {
        val instructionList = linearProgram.filterIsInstance<Instruction>()

        // find all registers, prepare empty graphs
        val allRegisters = mutableSetOf<Register>()
        instructionList.forEach {
            allRegisters.addAll(it.regsDefined)
            allRegisters.addAll(it.regsUsed)
        }

        val interferenceGraph = mutableMapOf(*allRegisters.map { it to mutableSetOf<Register>() }.toTypedArray())
        val copyGraph = mutableMapOf(*allRegisters.map { it to mutableSetOf<Register>() }.toTypedArray())

        // run analysis and fill the graphs
        val dataFlowResult = LivenessDataFlowAnalyzer.analyze(linearProgram)
        val outLiveRegisters = mutableMapOf(*dataFlowResult.outValues.map { (instr, regs) -> instr to (regs + instr.value.regsDefined.toSet()) }.toTypedArray())

        instructionList.forEach {
            for (definedReg in it.regsDefined)
                for (regLiveOnOutput in outLiveRegisters[Ref(it)]!!)
                    if (definedReg !== regLiveOnOutput && !(it is Instruction.InPlaceInstruction.MoveRR && it.regDest === definedReg && it.regSrc === regLiveOnOutput)) {
                        interferenceGraph[definedReg]!!.add(regLiveOnOutput)
                        interferenceGraph[regLiveOnOutput]!!.add(definedReg)
                    }

            if (it is Instruction.InPlaceInstruction.MoveRR) {
                copyGraph[it.regDest]!!.add(it.regSrc)
                copyGraph[it.regSrc]!!.add(it.regDest)
            }
        }

        return LivenessGraphs(interferenceGraph, copyGraph)
    }
}
