package compiler.input

data class Location(val row: Int, val column: Int) {
    override fun toString(): String =
        "(${this.row}:${this.column})"
}
