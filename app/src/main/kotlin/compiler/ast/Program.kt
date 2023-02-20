package compiler.ast

import compiler.input.LocationRange

data class Program(val globals: List<Global>) : AstNode {
    sealed class Global : AstNode {
        data class VariableDefinition(val variable: Variable, override val location: LocationRange? = null) : Global()
        data class FunctionDefinition(val function: Function, override val location: LocationRange? = null) : Global()

        override fun toSimpleString() = "<<global definition>>"

        override fun toExtendedString() = when (this) {
            is FunctionDefinition -> "definition of << ${this.function.toSimpleString()} >>"
            is VariableDefinition -> "definition of << ${this.variable.toSimpleString()} >>"
        }
    }

    override val location: LocationRange? get() = null

    override fun toSimpleString() = "<<the entire program>>"
    override fun toExtendedString() = "the entire program"
}
