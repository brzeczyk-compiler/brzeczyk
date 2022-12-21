package compiler.intermediate.generators

import compiler.ast.NamedNode
import compiler.intermediate.IFTNode

interface VariableAccessGenerator {

    fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode

    fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode
}
