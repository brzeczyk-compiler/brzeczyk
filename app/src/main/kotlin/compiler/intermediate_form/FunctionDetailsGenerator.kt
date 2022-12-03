package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.common.intermediate_form.FunctionDetailsGeneratorInterface

data class FunctionDetailsGenerator(
    val depth: ULong,
    val vars: Map<NamedNode, Boolean>,
    val parameters: List<Function.Parameter>
) : FunctionDetailsGeneratorInterface {

    override fun generateCall(args: List<IntermediateFormTreeNode>): FunctionDetailsGeneratorInterface.FunctionCallIntermediateForm {
        return TODO()
    }

    override fun genPrologue(): ControlFlowGraph {
        return TODO()
    }

    override fun genEpilogue(): ControlFlowGraph {
        return TODO()
    }

    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IntermediateFormTreeNode {
        // the caller is supposed to retrieve the value and assign it
        return TODO()
    }

    override fun genWrite(namedNode: NamedNode, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode {
        return TODO()
    }
}
