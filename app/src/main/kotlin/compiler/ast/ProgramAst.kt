package compiler.ast

data class ProgramAst(val globals: List<Global>) {
    sealed class Global {
        data class VariableDefinition(val variable: VariableAst) : Global()
        data class FunctionDefinition(val function: FunctionAst) : Global()
    }
}
