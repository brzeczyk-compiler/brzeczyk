package compiler.ast

sealed class TypeAst {
    object Unit : TypeAst()
    object Boolean : TypeAst()
    object Number : TypeAst()
}
