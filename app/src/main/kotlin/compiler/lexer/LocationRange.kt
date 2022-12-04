package compiler.lexer

data class LocationRange(val start: Location, val end: Location) {
    override fun toString(): String =
        "from (${this.start.row}, ${this.start.column}) to (${this.end.row}, ${this.end.column})"
}
