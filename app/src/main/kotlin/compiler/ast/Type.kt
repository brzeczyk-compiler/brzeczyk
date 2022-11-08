package compiler.ast

sealed class Type {
    object Unit : Type()
    object Boolean : Type()
    object Number : Type()
}
