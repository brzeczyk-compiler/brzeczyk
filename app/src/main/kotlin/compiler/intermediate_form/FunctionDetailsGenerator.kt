package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Variable

data class FunctionDetailsGenerator(
    val depth: ULong,
    val vars: Map<NamedNode, Boolean>,
    val parameters: List<Function.Parameter>
)
data class FunctionCallIntermediateForm(
    val callGraph: ControlFlowGraph,
    val result: IntermediateFormTreeNode?
)

fun generateCall(args: List<IntermediateFormTreeNode>): FunctionCallIntermediateForm {
    return TODO()
}

fun genPrologue(): ControlFlowGraph {
    return TODO()
}

fun genEpilogue(): ControlFlowGraph {
    return TODO()
}

fun genRead(variable: Variable, isDirect: Boolean): IntermediateFormTreeNode {
    // the caller is supposed to retrieve the value and assign it
    return TODO()
}

fun genWrite(variable: Variable, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode {
    return TODO()
}
