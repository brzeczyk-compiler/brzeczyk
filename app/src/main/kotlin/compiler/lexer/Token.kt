package compiler.lexer

import compiler.input.LocationRange

data class Token<TCat>(val category: TCat, val content: String, val location: LocationRange)
