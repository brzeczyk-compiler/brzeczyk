package compiler.ast

data class Program(val globals: List<Global>) {
    sealed class Global : AstNode() {
        data class VariableDefinition(val variable: Variable, override val location: NodeLocation? = null) : Global()
        data class FunctionDefinition(val function: Function, override val location: NodeLocation? = null) : Global()
    }
}
