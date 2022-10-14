package compiler.lexer.input

import compiler.lexer.Location
import java.io.Reader
import java.util.Stack

class InputImpl(private val source: Reader) : Input {

    private val characterBuffer = ArrayDeque<Char>()
    private var pos: Int = 0

    private var row: Int = 1
    private var col: Int = 1
    private val lineStack = Stack<Int>()

    private fun ensureNextCharIsAvailable(): Boolean {
        if (pos < characterBuffer.size)
            return true
        val nextChar = source.read()
        if (nextChar == -1)
            return false
        characterBuffer.addLast(nextChar.toChar())
        return true
    }

    override fun getLocation(): Location {
        return Location(row, col)
    }

    override fun rewind(count: Int) {
        if (count > pos)
            throw IndexOutOfBoundsException()
        pos -= count

        var countLeft = count
        while (col <= countLeft) {
            countLeft -= col
            row--
            col = lineStack.pop()
        }
        col -= countLeft
    }

    override fun flush() {
        characterBuffer.subList(0, pos).clear()
        pos = 0
        lineStack.clear()
    }

    override fun hasNext(): Boolean {
        return ensureNextCharIsAvailable()
    }

    override fun next(): Char {
        if (!ensureNextCharIsAvailable())
            throw NoSuchElementException()

        val nextChar = characterBuffer[pos++]
        if (nextChar == '\n') {
            lineStack.push(col)
            row++
            col = 1
        } else
            col++
        return nextChar
    }
}
