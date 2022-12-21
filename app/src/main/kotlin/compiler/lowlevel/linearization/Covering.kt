package compiler.lowlevel.linearization

import compiler.intermediate.IFTNode
import compiler.lowlevel.Instruction

interface Covering {
    fun coverUnconditional(iftNode: IFTNode): List<Instruction>
    fun coverConditional(iftNode: IFTNode, targetLabel: String, invert: Boolean): List<Instruction>
}
