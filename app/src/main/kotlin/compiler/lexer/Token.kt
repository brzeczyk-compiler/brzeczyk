package compiler.lexer

data class Token<TCat>(val category: TCat, val content: String, val location: LocationRange)
