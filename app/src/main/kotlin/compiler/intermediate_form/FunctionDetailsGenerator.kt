package compiler.intermediate_form

import compiler.ast.Variable
import compiler.common.intermediate_form.FunctionDetailsGeneratorInterface

class FunctionDetailsGenerator(
    val depth: Int,
    val vars: Map<Variable, Boolean>,
    val parameters: List<Variable>
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

    override fun genRead(variable: Variable, isDirect: Boolean): IntermediateFormTreeNode {
        // the caller is supposed to retrieve the value and assign it
        return TODO()
    }

    override fun genWrite(variable: Variable, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode {
        return TODO()
    }
}
