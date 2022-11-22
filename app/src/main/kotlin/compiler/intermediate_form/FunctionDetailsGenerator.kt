package compiler.intermediate_form

import compiler.ast.Variable

data class FunctionDetailsGenerator(
    val depth: Int,
    val vars: Map<Variable, Boolean>,
    val parameters: List<Variable>
)

fun generateCall(args: List<IntermediateFormTreeNode>): ControlFlowGraph {
    return TODO()
}

fun genPrologue(): ControlFlowGraph {
    return TODO()
}

fun genEpilogue(): ControlFlowGraph {
    return TODO()
}

fun genRead(variable: Variable, direct: Boolean): IntermediateFormTreeNode {
    // the caller is supposed to retrieve the value and assign it
    return TODO()
}

fun genWrite(variable: Variable, value: IntermediateFormTreeNode, direct: Boolean): IntermediateFormTreeNode {
    return TODO()
}
