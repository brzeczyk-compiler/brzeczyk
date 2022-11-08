package compiler.ast

object AstNodeTypes {
    sealed interface NamedNode
    sealed interface FunctionDefinition : NamedNode
}
