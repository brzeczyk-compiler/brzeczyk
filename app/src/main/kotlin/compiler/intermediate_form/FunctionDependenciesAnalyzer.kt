package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.ReferenceSet

object FunctionDependenciesAnalyzer {
    enum class VariableAccessMode {
        READ_ONLY,
        READ_WRITE
    }

    fun variablesUsedByFunctions(ast: Program): ReferenceMap<Function, ReferenceMap<Variable, VariableAccessMode>> {
        return TODO()
    }

    fun createCallGraph(ast: Program): ReferenceMap<Function, ReferenceSet<Function>> {
        return TODO()
    }
}
