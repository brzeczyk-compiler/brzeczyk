package compiler.common.intermediate_form

import compiler.ast.NamedNode
import compiler.intermediate_form.IntermediateFormTreeNode

interface VariableAccessGenerator {

    fun genRead(namedNode: NamedNode, isDirect: Boolean): IntermediateFormTreeNode

    fun genWrite(namedNode: NamedNode, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode
}
