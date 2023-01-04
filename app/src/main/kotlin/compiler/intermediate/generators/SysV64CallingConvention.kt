package compiler.intermediate.generators

import compiler.intermediate.CFGLinkType
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.IFTNode
import compiler.intermediate.Register

val argPositionToRegister = listOf(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.R8, Register.R9)
val callerSavedRegisters = listOf(Register.RAX, Register.RCX, Register.RDX, Register.RSI, Register.RDI, Register.R8, Register.R9, Register.R10, Register.R11)
const val stackAlignmentInBytes = 16

object SysV64CallingConvention {
    fun genCall(targetFunction: IFTNode.MemoryLabel, args: List<IFTNode>, returnsValue: Boolean): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        val moveArgsCFGBuilder = ControlFlowGraphBuilder()
        val usedRegisters = mutableListOf<Register>()

        // First, move arguments to appropriate registers (or push to stack) according to call convention.
        for ((arg, register) in args zip argPositionToRegister) {
            val node = IFTNode.RegisterWrite(register, arg)
            moveArgsCFGBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, node)
            usedRegisters.add(register)
        }

        // Before pushing args stack will be aligned, so code adds links to separate CFGBuilder and merges them later
        var stackMovement = 0L
        for (arg in args.drop(argPositionToRegister.size).reversed()) {
            moveArgsCFGBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, IFTNode.StackPush(arg))
            stackMovement += memoryUnitSize.toLong()
        }

        val cfgBuilder = ControlFlowGraphBuilder()
        // Align stack
        if (stackMovement % stackAlignmentInBytes != 0L) {
            val toAdd = stackAlignmentInBytes - stackMovement % stackAlignmentInBytes
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(
                    Register.RSP,
                    IFTNode.Subtract(
                        IFTNode.RegisterRead(Register.RSP),
                        IFTNode.Const(toAdd)
                    )
                )
            )
            stackMovement += toAdd
        }

        if (moveArgsCFGBuilder.entryTreeRoot != null)
            cfgBuilder.mergeUnconditionally(moveArgsCFGBuilder.build())

        // Add call instruction to actually call a given function
        cfgBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.Call(targetFunction, usedRegisters, callerSavedRegisters)
        )

        // Abandon arguments that were previously put on stack
        if (stackMovement > 0)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(
                    Register.RSP,
                    IFTNode.Add(
                        IFTNode.RegisterRead(Register.RSP),
                        IFTNode.Const(stackMovement)
                    )
                )
            )

        // At the end create IFTNode to get function result
        val readResultNode: IFTNode? =
            if (returnsValue) IFTNode.RegisterRead(Register.RAX)
            else null

        val furtherContinuationNode = null // TODO()
        return FunctionDetailsGenerator.FunctionCallIntermediateForm(cfgBuilder.build(), readResultNode, furtherContinuationNode)
    }
}
