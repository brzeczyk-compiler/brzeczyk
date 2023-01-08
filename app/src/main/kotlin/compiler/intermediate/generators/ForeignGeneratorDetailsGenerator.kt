package compiler.intermediate.generators

import compiler.ast.NamedNode
import compiler.ast.Statement
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.IFTNode

class ForeignGeneratorDetailsGenerator(
    initLabel: IFTNode.MemoryLabel,
    resumeLabel: IFTNode.MemoryLabel,
    finalizeLabel: IFTNode.MemoryLabel
) : GeneratorDetailsGenerator {
    override val initFDG = ForeignFunctionDetailsGenerator(initLabel, 1)
    override val resumeFDG = ForeignFunctionDetailsGenerator(resumeLabel, 2)
    override val finalizeFDG = ForeignFunctionDetailsGenerator(finalizeLabel, 0)

    override fun genInitCall(args: List<IFTNode>): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        return initFDG.genCall(args)
    }

    override fun genResumeCall(framePointer: IFTNode, savedState: IFTNode): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        return resumeFDG.genCall(listOf(framePointer, savedState))
    }

    override fun genFinalizeCall(framePointer: IFTNode): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        return finalizeFDG.genCall(listOf(framePointer))
    }

    override fun genInit() = throw NotImplementedError()

    override fun genResume(mainBody: ControlFlowGraph) = throw NotImplementedError()

    override fun genYield(value: IFTNode) = throw NotImplementedError()

    override fun getNestedForeachFramePointerAddress(foreachLoop: Statement.ForeachLoop) = throw NotImplementedError()

    override fun genFinalize() = throw NotImplementedError()

    override fun genRead(namedNode: NamedNode, isDirect: Boolean) = throw NotImplementedError()

    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean) = throw NotImplementedError()
}
