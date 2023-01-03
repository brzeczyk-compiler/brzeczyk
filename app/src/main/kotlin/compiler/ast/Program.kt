package compiler.ast

import compiler.input.LocationRange

data class Program(val globals: List<Global>) : AstNode {
    override val location: LocationRange? get() = null

    sealed class Global : AstNode {
        data class VariableDefinition(val variable: Variable, override val location: LocationRange? = null) : Global()
        data class FunctionDefinition(val function: Function, override val location: LocationRange? = null) : Global()
    }
}
