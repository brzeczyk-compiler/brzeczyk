package compiler

import compiler.common.diagnostics.Diagnostics
import compiler.lexer.Lexer
import compiler.lexer.input.InputImpl
import compiler.lexer.lexer_grammar.TokenType
import compiler.lexer.lexer_grammar.Tokens
import compiler.parser.ParseTree
// import compiler.parser.Parser
// import compiler.parser.grammar.ParserGrammar
import compiler.parser.grammar.Symbol
import java.io.Reader

class Compiler(val diagnostics: Diagnostics) {
    fun process(input: Reader) {
        val lexer = Lexer<TokenType>(Tokens().getTokens(), diagnostics)
        val tokenSequence = lexer.process(InputImpl(input))
        val leaves: Sequence<ParseTree<Symbol>> = tokenSequence
            .map { ParseTree.Leaf(it.start, it.end, Symbol.Terminal(it.category)) }

        // consume the elements so that Lexer actually tries to evaluate
        leaves.forEach { println(it) }

        // val parseTree = Parser<Symbol>(ParserGrammar.getGrammar(), diagnostics).process(leaves)
    }
}
