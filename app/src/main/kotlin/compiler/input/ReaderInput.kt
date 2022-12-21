package compiler.input

import java.io.Reader
import java.util.Stack

class ReaderInput(private val source: Reader) : Input {

    private val characterBuffer = ArrayDeque<Char>()
    private var position: Int = 0

    private var row: Int = 1
    private var column: Int = 1
    private val lineLengthStack = Stack<Int>()

    private fun ensureNextCharIsAvailable(): Boolean {
        if (position <= characterBuffer.lastIndex)
            return true
        val nextChar = source.read()
        if (nextChar == -1)
            return false
        characterBuffer.addLast(nextChar.toChar())
        return true
    }

    override fun getLocation(): Location {
        return Location(row, column)
    }

    override fun rewind(count: Int) {
        if (count > position)
            throw IndexOutOfBoundsException()
        position -= count

        var charsLeft = count
        while (column <= charsLeft) {
            charsLeft -= column
            row--
            column = lineLengthStack.pop()
        }
        column -= charsLeft
    }

    override fun flush() {
        characterBuffer.subList(0, position).clear()
        position = 0
        lineLengthStack.clear()
    }

    override fun hasNext(): Boolean {
        return ensureNextCharIsAvailable()
    }

    override fun next(): Char {
        if (!ensureNextCharIsAvailable())
            throw NoSuchElementException()

        val nextChar = characterBuffer[position++]
        if (nextChar == '\n') {
            lineLengthStack.push(column)
            row++
            column = 1
        } else {
            column++
        }
        return nextChar
    }
}
