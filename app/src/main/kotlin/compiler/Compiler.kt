package compiler

import compiler.ast.AstFactory
import compiler.common.diagnostics.Diagnostics
import compiler.lexer.Lexer
import compiler.lexer.input.InputImpl
import compiler.lexer.lexer_grammar.TokenType
import compiler.lexer.lexer_grammar.Tokens
import compiler.parser.ParseTree
import compiler.parser.Parser
import compiler.parser.grammar.ParserGrammar
import compiler.parser.grammar.Symbol
import compiler.semantic_analysis.Resolver
import java.io.Reader

// The main class used to compile a source code into an executable machine code.
class Compiler(val diagnostics: Diagnostics) {
    // The type of exceptions thrown when, given a correct input (satisfying the invariants but not necessarily semantically correct),
    // a compilation phase is unable to produce a correct output, and so the entire compilation pipeline must be stopped.
    abstract class CompilationFailed : Throwable()

    private val lexer = Lexer(Tokens.getTokens(), diagnostics)
    private val parser = Parser(ParserGrammar.getGrammar(), diagnostics)

    fun process(input: Reader) {
        try {
            val tokenSequence = lexer.process(InputImpl(input))

            val leaves: Sequence<ParseTree<Symbol>> = tokenSequence.filter { it.category != TokenType.TO_IGNORE } // TODO: move this transformation somewhere else
                .map { ParseTree.Leaf(it.location, Symbol.Terminal(it.category), it.content) }

            val parseTree = parser.process(leaves)

            val ast = AstFactory.createFromParseTree(parseTree, diagnostics) // TODO: make AstFactory and Resolver a class for consistency with Lexer and Parser

            val programProperties = Resolver.resolveProgram(ast, diagnostics)

            // TODO: generate the code
        } catch (_: CompilationFailed) { } finally {
            println(diagnostics)
        }
    }
}
