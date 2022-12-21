package compiler.intermediate.generators

import compiler.ast.NamedNode
import compiler.intermediate.IFTNode

class ForeignFunctionDetailsGenerator : FunctionDetailsGenerator {
    override fun genCall(args: List<IFTNode>): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        TODO()
    }

    override fun genPrologue() = throw NotImplementedError()

    override fun genEpilogue() = throw NotImplementedError()

    override val spilledRegistersOffset = throw NotImplementedError()

    override fun genRead(namedNode: NamedNode, isDirect: Boolean) = throw NotImplementedError()

    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean) = throw NotImplementedError()
}
