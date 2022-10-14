package compiler.lexer.input

import compiler.lexer.Location

class InputImpl : Input {

    override fun getLocation(): Location {
        // TODO
        return Location(0, 0)
    }

    override fun rewind(count: Int) {
        // TODO
    }

    override fun flush() {
        // TODO
    }

    override fun hasNext(): Boolean {
        // TODO
        return true
    }

    override fun next(): Char {
        // TODO
        return Char.MIN_VALUE
    }
}
