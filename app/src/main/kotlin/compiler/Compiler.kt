package compiler

import compiler.analysis.ProgramAnalyzer
import compiler.diagnostics.Diagnostics
import compiler.input.ReaderInput
import compiler.intermediate.ControlFlow.createGraphForProgram
import compiler.lexer.Lexer
import compiler.parser.ParseTree
import compiler.parser.Parser
import compiler.syntax.AstFactory
import compiler.syntax.LanguageGrammar
import compiler.syntax.LanguageTokens
import compiler.syntax.Symbol
import compiler.syntax.TokenType
import java.io.Reader

// The main class used to compile a source code into an executable machine code.
class Compiler(val diagnostics: Diagnostics) {
    // The type of exceptions thrown when, given a correct input (satisfying the invariants but not necessarily semantically correct),
    // a compilation phase is unable to produce a correct output, and so the entire compilation pipeline must be stopped.
    abstract class CompilationFailed : Throwable()

    private val lexer = Lexer(LanguageTokens.getTokens(), diagnostics)
    private val parser = Parser(LanguageGrammar.getGrammar(), diagnostics)

    fun process(input: Reader) {
        try {
            val tokenSequence = lexer.process(ReaderInput(input))

            val leaves: Sequence<ParseTree<Symbol>> = tokenSequence.filter { it.category != TokenType.TO_IGNORE } // TODO: move this transformation somewhere else
                .map { ParseTree.Leaf(it.location, Symbol.Terminal(it.category), it.content) }

            val parseTree = parser.process(leaves)

            val ast = AstFactory.createFromParseTree(parseTree, diagnostics) // TODO: make AstFactory and Resolver a class for consistency with Lexer and Parser

            val programProperties = ProgramAnalyzer.analyzeProgram(ast, diagnostics)

            val cfg = createGraphForProgram(ast, programProperties, diagnostics, diagnostics.hasAnyError())
            // TODO: generate the code
        } catch (_: CompilationFailed) { }

        println(diagnostics)
    }
}
