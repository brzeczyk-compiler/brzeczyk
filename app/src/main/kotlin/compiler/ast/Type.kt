package compiler.ast

sealed class Type {
    object Unit : Type()
    object Boolean : Type()
    object Number : Type()

    override fun toString(): String = when (this) {
        Boolean -> "Czy"
        Number -> "Liczba"
        Unit -> "Nic"
    }
}
