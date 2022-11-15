package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Variable
import compiler.common.semantic_analysis.ReferenceMap

object FunctionDependenciesAnalyzer {
    fun variablesUsedByFunctions(ast: Program): ReferenceMap<Function, Pair<List<Variable>, List<Variable>>> { // (accessed, modified)
        return TODO()
    }

    fun createCallGraph(ast: Program): Set<Pair<Function, Function>> {
        return TODO()
    }
}
