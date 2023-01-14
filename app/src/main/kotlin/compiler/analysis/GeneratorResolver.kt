package compiler.analysis

import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Function
import compiler.ast.Variable
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf

object GeneratorResolver {
    fun computeGeneratorProperties(ast: Program): Map<Ref<Function>, List<Ref<Statement.ForeachLoop>>> {
        return TODO()
    }
}