package compiler.intermediate.generators

import compiler.ast.NamedNode
import compiler.intermediate.IFTNode

class ForeignFunctionDetailsGenerator(
    private val memoryLabel: IFTNode.MemoryLabel,
    private val returnsValue: Boolean
) : FunctionDetailsGenerator {
    override fun genCall(args: List<IFTNode>): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        return SysV64CallingConvention.genCall(memoryLabel, args, returnsValue)
    }

    override fun genPrologue() = throw NotImplementedError()

    override fun genEpilogue() = throw NotImplementedError()

    override fun genInit() = throw NotImplementedError()
    override fun genFinalize() = throw NotImplementedError()
    override fun genYield(value: IFTNode) = throw NotImplementedError()

    override val spilledRegistersRegionOffset get() = throw NotImplementedError()

    override val spilledRegistersRegionSize get() = throw NotImplementedError()

    override val identifier: String = memoryLabel.label

    override fun genRead(namedNode: NamedNode, isDirect: Boolean) = throw NotImplementedError()

    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean) = throw NotImplementedError()
}
