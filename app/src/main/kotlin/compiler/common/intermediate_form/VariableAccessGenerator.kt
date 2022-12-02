package compiler.common.intermediate_form

import compiler.ast.Variable
import compiler.intermediate_form.IntermediateFormTreeNode

interface VariableAccessGenerator {

    fun genRead(variable: Variable, isDirect: Boolean): IntermediateFormTreeNode

    fun genWrite(variable: Variable, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode
}
