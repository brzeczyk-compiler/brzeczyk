package compiler.ast

sealed interface NamedNode : AstNode {
    val name: String
}
