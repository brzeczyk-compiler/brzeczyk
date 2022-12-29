package compiler.ast

import compiler.input.LocationRange

data class Program(val globals: List<Global>) {
    var staticFunctionDepth: Int = 0

    sealed class Global : AstNode {
        data class VariableDefinition(val variable: Variable, override val location: LocationRange? = null) : Global()
        data class FunctionDefinition(val function: Function, override val location: LocationRange? = null) : Global()
    }
}
