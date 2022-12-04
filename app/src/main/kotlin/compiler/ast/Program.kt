package compiler.ast

import compiler.lexer.LocationRange

data class Program(val globals: List<Global>) {
    sealed class Global : AstNode() {
        data class VariableDefinition(val variable: Variable, override val location: LocationRange? = null) : Global()
        data class FunctionDefinition(val function: Function, override val location: LocationRange? = null) : Global()
    }
}
