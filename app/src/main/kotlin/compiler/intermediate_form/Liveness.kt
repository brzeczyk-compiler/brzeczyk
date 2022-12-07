package compiler.intermediate_form

object Liveness {
    data class LivenessGraphs(
        val interferenceGraph: Map<Register, Set<Register>>,
        val copyGraph: Map<Register, Set<Register>>
    )

    fun computeLiveness(linearProgram: List<Asmable>): LivenessGraphs = TODO()
}
