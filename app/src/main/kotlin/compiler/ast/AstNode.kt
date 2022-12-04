package compiler.ast

import compiler.lexer.LocationRange

abstract class AstNode {
    abstract val location: LocationRange?
}
