package compiler.lexer

data class LocationRange(val start: Location, val end: Location) {
    override fun toString(): String =
        "from ${this.start} to ${this.end}"
}
