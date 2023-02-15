package compiler

import compiler.analysis.BuiltinFunctions
import compiler.analysis.ProgramAnalyzer
import compiler.ast.Type
import compiler.diagnostics.Diagnostics
import compiler.input.ReaderInput
import compiler.intermediate.ControlFlowPlanner
import compiler.intermediate.DetailsGeneratorsBuilder
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
    private val astFactory = AstFactory(diagnostics)
    private val programAnalyzer = ProgramAnalyzer(diagnostics)
    private val controlFlowPlanner = ControlFlowPlanner(diagnostics)
    private val covering = DynamicCoveringBuilder(InstructionSet.getInstructionSet())
    private val allocation = Allocation(ColoringAllocation)
    private val linearization = Linearization(covering)

    fun process(input: Reader, output: Writer): Boolean {
        try {
            // Front-end

            val tokens = lexer.process(ReaderInput(input))

            val tokensAsLeaves: Sequence<ParseTree<Symbol>> = tokens
                .filter { it.category != TokenType.IGNORED }
                .map { ParseTree.Leaf(it.location, Symbol.Terminal(it.category), it.content) }

            val parseTree = parser.process(tokensAsLeaves)

            val initialProgram = astFactory.createFromParseTree(parseTree)

            val program = BuiltinFunctions.addBuiltinFunctions(initialProgram)

            val programProperties = programAnalyzer.analyze(program)

            val mainFunction = programAnalyzer.extractMainFunction(program)

            // Intermediate

            val (functionDetailsGenerators, generatorDetailsGenerators) = DetailsGeneratorsBuilder.createDetailsGenerators(
                program,
                programProperties,
                diagnostics.hasAnyErrors()
            )

            val bareFunctionControlFlowGraphs = controlFlowPlanner.createGraphsForProgram(
                program,
                programProperties,
                functionDetailsGenerators,
                generatorDetailsGenerators
            )

            val functionControlFlowGraphs = bareFunctionControlFlowGraphs.flatMap { (function, bareControlFlowGraph) ->
                if (function.value.isGenerator) {
                    val generatorDetailsGenerator = generatorDetailsGenerators[function]!!
                    val initControlFlowGraph = generatorDetailsGenerator.genInit()
                    val resumeControlFlowGraph = generatorDetailsGenerator.genResume(bareControlFlowGraph)
                    val finalizeControlFlowGraph = generatorDetailsGenerator.genFinalize()
                    listOf(
                        Pair(initControlFlowGraph, generatorDetailsGenerator.initFDG),
                        Pair(resumeControlFlowGraph, generatorDetailsGenerator.resumeFDG),
                        Pair(finalizeControlFlowGraph, generatorDetailsGenerator.finalizeFDG)
                    )
                } else {
                    val functionDetailsGenerator = functionDetailsGenerators[function]!!
                    val prologue = functionDetailsGenerator.genPrologue()
                    val epilogue = functionDetailsGenerator.genEpilogue()
                    val controlFlowGraph = controlFlowPlanner.attachPrologueAndEpilogue(bareControlFlowGraph, prologue, epilogue)
                    listOf(
                        Pair(controlFlowGraph, functionDetailsGenerator)
                    )
                }
            }

            // Back-end

            if (diagnostics.hasAnyErrors())
                return false

            val displayStorage = DisplayStorage(programProperties.staticDepth)
            val globalVariableStorage = GlobalVariableStorage(program)

            val mainFunctionLabel = functionDetailsGenerators[Ref(mainFunction)]!!.identifier
            val ignoreMainReturnValue = mainFunction!!.returnType != Type.Number

            val foreignIdentifiers = BuiltinFunctions.internallyUsedExternalSymbols +
                functionDetailsGenerators
                    .filterKeys { !it.value.isLocal }
                    .map { it.value.identifier } +
                generatorDetailsGenerators
                    .filterKeys { !it.value.isLocal }
                    .values
                    .flatMap { listOf(it.initFDG, it.resumeFDG, it.finalizeFDG) }
                    .map { it.identifier }

            val functions = functionControlFlowGraphs.map { (controlFlowGraph, functionDetailsGenerator) ->
                val code = linearization.linearize(controlFlowGraph)

                val liveness = Liveness.computeLiveness(code)

                val allocationResult = allocation.allocateRegistersWithSpillsHandling(
                    code,
                    liveness,
                    Allocation.HARDWARE_REGISTERS,
                    Allocation.AVAILABLE_REGISTERS,
                    Allocation.POTENTIAL_SPILL_HANDLING_REGISTERS,
                    functionDetailsGenerator.spilledRegistersRegionOffset
                )

                functionDetailsGenerator.spilledRegistersRegionSize.settledValue = allocationResult.spilledOffset.toLong()

                CodeSection.FunctionCode(functionDetailsGenerator.identifier, allocationResult.code, allocationResult.allocatedRegisters)
            }

            val codeSection = CodeSection(
                mainFunctionLabel,
                ignoreMainReturnValue,
                foreignIdentifiers,
                functions
            )

            AsmFile.printFile(
                PrintWriter(output),
                displayStorage::writeAsm,
                globalVariableStorage::writeAsm,
                codeSection::writeAsm
            )

            return true
        } catch (_: CompilationFailed) { }

        return false
    }
}
