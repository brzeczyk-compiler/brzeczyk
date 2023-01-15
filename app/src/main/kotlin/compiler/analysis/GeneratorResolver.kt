package compiler.analysis

import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.utils.Ref

object GeneratorResolver {
    fun computeGeneratorProperties(ast: Program): Map<Ref<Function>, List<Ref<Statement.ForeachLoop>>> {
        return emptyMap() // TODO
    }
}
