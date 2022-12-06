package compiler.intermediate_form

object Liveliness {
    data class LivelinessGraphs(
        val interferenceGraph: Map<Register, Set<Register>>,
        val copyGraph: Map<Register, Set<Register>>
    )

    fun computeLiveliness(linearProgram: List<AsmAble>): LivelinessGraphs = TODO()
}
