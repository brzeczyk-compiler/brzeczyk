package compiler.ast

import compiler.lexer.LocationRange

sealed interface AstNode {
    val location: LocationRange?
}
