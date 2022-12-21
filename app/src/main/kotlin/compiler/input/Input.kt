package compiler.input

interface Input : Iterator<Char> {

    fun getLocation(): Location

    fun rewind(count: Int)

    fun flush()
}
