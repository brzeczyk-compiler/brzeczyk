package compiler.ast

data class Program(val globals: List<Global>) {
    sealed class Global {
        data class VariableDefinition(val variable: Variable) : Global()
        data class FunctionDefinition(val function: Function) : Global()
    }
}
