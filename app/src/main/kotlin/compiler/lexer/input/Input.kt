package compiler.lexer.input

import compiler.lexer.Location

interface Input : Iterator<Char> {

    fun getLocation(): Location

    fun rewind(count: Int)
}
