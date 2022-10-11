package compiler.lexer

import compiler.lexer.dfa.Dfa
import compiler.lexer.input.Input
import java.util.stream.Stream

class Lexer<TCat>(val dfaMap: Map<TCat, Dfa>) {

    fun process(input: Input): Stream<Token<TCat>> {
        // TODO
        return Stream.empty()
    }
}
