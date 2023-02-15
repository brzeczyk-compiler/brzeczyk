package compiler.ast

import compiler.input.LocationRange

sealed interface AstNode {
    val location: LocationRange?

    fun toSimpleString(): String

    fun toExtendedString(): String
}
