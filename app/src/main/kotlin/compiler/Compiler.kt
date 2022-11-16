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
import compiler.semantic_analysis.NameResolver
import compiler.semantic_analysis.TypeChecker
import compiler.semantic_analysis.VariablePropertiesAnalyzer
import java.io.Reader

class Compiler(val diagnostics: Diagnostics) {

    private val lexer = Lexer(Tokens.getTokens(), diagnostics)
    private val parser = Parser(ParserGrammar.getGrammar(), diagnostics)

    fun process(input: Reader) {
        val tokenSequence = lexer.process(InputImpl(input))
        val leaves: Sequence<ParseTree<Symbol>> = tokenSequence.filter { it.category != TokenType.TO_IGNORE }
            .map { ParseTree.Leaf(it.start, it.end, Symbol.Terminal(it.category), it.content) }

        val parseTree = parser.process(leaves)

        val ast = AstFactory.createFromParseTree(parseTree, diagnostics)
        val nameResolution = NameResolver.calculateNameResolution(ast, diagnostics)
        // val argumentResolution = ArgumentResolver.calculateArgumentToParameterResolution(ast, diagnostics)
        val expressionTypes = TypeChecker.calculateTypes(ast, nameResolution, diagnostics)
        val variableProperties = VariablePropertiesAnalyzer.calculateVariableProperties(ast, nameResolution, diagnostics)
    }
}
