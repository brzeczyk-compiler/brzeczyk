package compiler.intermediate.generators

import compiler.analysis.VariablePropertiesAnalyzer
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.NamedNode
import compiler.ast.Variable
import compiler.intermediate.IFTNode
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf

class GlobalVariableAccessGenerator(variableProperties: Map<Ref<AstNode>, VariablePropertiesAnalyzer.VariableProperties>) : VariableAccessGenerator {

    companion object {
        const val VARIABLE_SIZE = 8L
        const val GLOBALS_MEMORY_LABEL = "globals"
    }

    private val offsets = mutableKeyRefMapOf<NamedNode, Long>().apply {
        this.putAll(
            variableProperties
                .filter { it.value.owner === VariablePropertiesAnalyzer.GlobalContext }
                .filter { (it.key.value as Variable).kind != Variable.Kind.CONSTANT }
                .asIterable().sortedBy { (it.key.value as Variable).name }
                .mapIndexed { index, value ->
                    Ref(value.key.value as Variable) to index.toLong() * VARIABLE_SIZE
                }
        )
    }

    private fun getMemoryAddress(namedNode: NamedNode) =
        IFTNode.Add(
            IFTNode.MemoryLabel(GLOBALS_MEMORY_LABEL),
            IFTNode.Const(offsets[Ref(namedNode)]!!)
        )

    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode =
        if (Ref(namedNode) in offsets)
            IFTNode.MemoryRead(getMemoryAddress(namedNode))
        else IFTNode.Const(
            (namedNode as Variable).value.let { Expression.getValueOfLiteral(it!!)!! }
        )

    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode =
        IFTNode.MemoryWrite(getMemoryAddress(namedNode), value)
}
