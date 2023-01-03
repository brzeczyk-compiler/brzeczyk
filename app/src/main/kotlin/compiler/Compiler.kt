package compiler

import compiler.analysis.BuiltinFunctions
import compiler.analysis.ProgramAnalyzer
import compiler.ast.Function
import compiler.ast.Type
import compiler.diagnostics.Diagnostics
import compiler.input.ReaderInput
import compiler.intermediate.ControlFlow.createGraphForProgram
import compiler.intermediate.FunctionDependenciesAnalyzer
import compiler.lexer.Lexer
import compiler.lowlevel.AsmFile
import compiler.lowlevel.CodeSection
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
import compiler.utils.Ref
import java.io.PrintWriter
import java.io.Reader
import java.io.Writer

// The main class used to compile a source code into an executable machine code.
class Compiler(val diagnostics: Diagnostics) {
    // The type of exceptions thrown when, given a correct input (satisfying the invariants but not necessarily semantically correct),
    // a compilation phase is unable to produce a correct output, and so the entire compilation pipeline must be stopped.
    abstract class CompilationFailed : Throwable()

    private val lexer = Lexer(LanguageTokens.getTokens(), diagnostics)
    private val parser = Parser(LanguageGrammar.getGrammar(), diagnostics)
    private val covering = DynamicCoveringBuilder(InstructionSet.getInstructionSet())

    fun process(input: Reader, output: Writer): Boolean {
        try {
            val tokenSequence = lexer.process(ReaderInput(input))

            val leaves: Sequence<ParseTree<Symbol>> = tokenSequence.filter { it.category != TokenType.TO_IGNORE } // TODO: move this transformation somewhere else
                .map { ParseTree.Leaf(it.location, Symbol.Terminal(it.category), it.content) }

            val parseTree = parser.process(leaves)

            val ast = AstFactory.createFromParseTree(parseTree, diagnostics) // TODO: make AstFactory and Resolver a class for consistency with Lexer and Parser

            val astWithBuiltinFunctions = BuiltinFunctions.addBuiltinFunctions(ast)

            val programProperties = ProgramAnalyzer.analyzeProgram(astWithBuiltinFunctions, diagnostics)

            val functionDetailsGenerators = FunctionDependenciesAnalyzer.createFunctionDetailsGenerators(
                astWithBuiltinFunctions,
                programProperties.variableProperties,
                programProperties.functionReturnedValueVariables,
                diagnostics.hasAnyError()
            )

            val functionCFGs = createGraphForProgram(astWithBuiltinFunctions, programProperties, functionDetailsGenerators, diagnostics)

            val mainFunction = FunctionDependenciesAnalyzer.extractMainFunction(ast, diagnostics)

            if (diagnostics.hasAnyError())
                return false

            val linearFunctions = functionCFGs.mapValues { Linearization.linearize(it.value, covering) }

            val finalCode = linearFunctions.mapValues {
                Allocation.allocateRegistersWithSpillsHandling(
                    it.value,
                    Liveness.computeLiveness(it.value),
                    Allocation.HARDWARE_REGISTERS,
                    Allocation.AVAILABLE_REGISTERS,
                    Allocation.POTENTIAL_SPILL_HANDLING_REGISTERS,
                    ColoringAllocation,
                    functionDetailsGenerators[it.key]!!.spilledRegistersRegionOffset
                )
            }

            finalCode.entries.forEach { functionDetailsGenerators[it.key]!!.spilledRegistersRegionSize.settledValue = it.value.spilledOffset.toLong() }

            AsmFile.printFile(
                PrintWriter(output),
                DisplayStorage(programProperties.staticDepth)::writeAsm,
                GlobalVariableStorage(ast)::writeAsm,
                CodeSection(
                    functionDetailsGenerators[Ref(mainFunction)]!!.identifier,
                    mainFunction!!.returnType != Type.Number,
                    functionDetailsGenerators
                        .filter { it.key.value.implementation is Function.Implementation.Foreign }
                        .map { it.value.identifier },
                    finalCode.map { functionCode ->
                        functionDetailsGenerators[functionCode.key]!!.identifier to
                            CodeSection.FunctionCode(
                                functionCode.value.linearProgram,
                                functionCode.value.allocatedRegisters
                            )
                    }.toMap()
                )::writeAsm
            )

            return true
        } catch (_: CompilationFailed) { }

        return false
    }
}
