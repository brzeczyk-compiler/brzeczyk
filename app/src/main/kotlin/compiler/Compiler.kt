package compiler

import compiler.analysis.ProgramAnalyzer
import compiler.ast.Program
import compiler.diagnostics.Diagnostic
import compiler.diagnostics.Diagnostics
import compiler.input.ReaderInput
import compiler.intermediate.ControlFlow.createGraphForProgram
import compiler.lexer.Lexer
import compiler.lowlevel.allocation.Allocation
import compiler.lowlevel.allocation.ColoringAllocation
import compiler.lowlevel.dataflow.Liveness
import compiler.lowlevel.linearization.DynamicCoveringBuilder
import compiler.lowlevel.linearization.InstructionSet
import compiler.lowlevel.linearization.Linearization
import compiler.lowlevel.storage.DisplayStorage
import compiler.lowlevel.storage.GlobalVariableStorage
import compiler.parser.ParseTree
import compiler.parser.Parser
import compiler.syntax.AstFactory
import compiler.syntax.LanguageGrammar
import compiler.syntax.LanguageTokens
import compiler.syntax.Symbol
import compiler.syntax.TokenType
import java.io.PrintWriter
import java.io.Reader

// The main class used to compile a source code into an executable machine code.
class Compiler(val diagnostics: Diagnostics) {
    // The type of exceptions thrown when, given a correct input (satisfying the invariants but not necessarily semantically correct),
    // a compilation phase is unable to produce a correct output, and so the entire compilation pipeline must be stopped.
    abstract class CompilationFailed : Throwable()

    private val lexer = Lexer(LanguageTokens.getTokens(), diagnostics)
    private val parser = Parser(LanguageGrammar.getGrammar(), diagnostics)
    private val covering = DynamicCoveringBuilder(InstructionSet.getInstructionSet())

    fun process(input: Reader, output: PrintWriter): Boolean {
        try {
            val tokenSequence = lexer.process(ReaderInput(input))

            val leaves: Sequence<ParseTree<Symbol>> = tokenSequence.filter { it.category != TokenType.TO_IGNORE } // TODO: move this transformation somewhere else
                .map { ParseTree.Leaf(it.location, Symbol.Terminal(it.category), it.content) }

            val parseTree = parser.process(leaves)

            val ast = AstFactory.createFromParseTree(parseTree, diagnostics) // TODO: make AstFactory and Resolver a class for consistency with Lexer and Parser

            val programProperties = ProgramAnalyzer.analyzeProgram(ast, diagnostics)

            val functionCFGs = createGraphForProgram(ast, programProperties, diagnostics, diagnostics.hasAnyError())

            if (!ast.globals.any { it is Program.Global.FunctionDefinition && it.function.name == "główna" }) {
                diagnostics.report(Diagnostic.MainFunctionNotFound())
            }

            if (diagnostics.hasAnyError())
                return false

            val linearFunctions = functionCFGs.mapValues { Linearization.linearize(it.value, covering) }

//            for ((function, instructions) in linearFunctions.entries) {
//                println(function)
//                for (instruction in instructions) println(instruction)
//            }

            val registerAllocation = linearFunctions.mapValues {
                Allocation.allocateRegistersWithSpillsHandling(
                    it.value,
                    Liveness.computeLiveness(it.value),
                    Allocation.REGISTER_ORDER,
                    ColoringAllocation
                )
            }

            DisplayStorage(ast).writeAsm(output)
            GlobalVariableStorage(ast).writeAsm(output)

            registerAllocation.forEach { function ->
                function.value.linearProgram.forEach {
                    it.writeAsm(output, function.value.allocatedRegisters)
                }
            }

            // TODO:
            // - make sure "główna" is present and generate a jump to it from "main",
            // - linearize CFG for each function,
            // - run liveness analysis on each linear function code,
            // - run register allocation on each linear function code,
            // - write ASM to output (display, global variables, functions)

            return true
        } catch (_: CompilationFailed) { }

        return false
    }
}
