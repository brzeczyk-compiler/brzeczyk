package compiler

import compiler.analysis.BuiltinFunctions
import compiler.analysis.ProgramAnalyzer
import compiler.ast.Function
import compiler.ast.Program
import compiler.diagnostics.Diagnostic
import compiler.diagnostics.Diagnostics
import compiler.input.ReaderInput
import compiler.intermediate.ControlFlow.createGraphForProgram
import compiler.intermediate.FunctionDependenciesAnalyzer
import compiler.lexer.Lexer
import compiler.lowlevel.Instruction
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

            val astWithBuiltinFunctions = BuiltinFunctions.addBuiltinFunctions(ast)

            val programProperties = ProgramAnalyzer.analyzeProgram(astWithBuiltinFunctions, diagnostics)

            val functionDetailsGenerators = FunctionDependenciesAnalyzer.createFunctionDetailsGenerators(
                astWithBuiltinFunctions,
                programProperties.variableProperties,
                programProperties.functionReturnedValueVariables,
                diagnostics.hasAnyError()
            )

            val functionCFGs = createGraphForProgram(astWithBuiltinFunctions, programProperties, functionDetailsGenerators, diagnostics)

            val mainFunction = (
                ast.globals.find { it is Program.Global.FunctionDefinition && it.function.name == "główna" } as Program.Global.FunctionDefinition?
                )?.function
            if (mainFunction == null) {
                diagnostics.report(Diagnostic.MainFunctionNotFound())
            }

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

            output.println("SECTION .bss")
            DisplayStorage(programProperties.staticDepth).writeAsm(output)

            output.println("\nSECTION .data")
            GlobalVariableStorage(ast).writeAsm(output)

            output.println("\nSECTION .text")
            functionDetailsGenerators
                .filter { it.key.implementation is Function.Implementation.Foreign }
                .forEach { output.println("extern ${it.value.identifier}") }
            output.println(
                """
                    global main
                    main:
                        call ${functionDetailsGenerators[mainFunction]!!.identifier}
                        ret
                    
                """.trimIndent()
            )

            finalCode.forEach { functionCode ->
                output.println("${functionDetailsGenerators[functionCode.key]!!.identifier}:")
                functionCode.value.linearProgram
                    .filterNot {
                        it is Instruction.InPlaceInstruction.MoveRR &&
                            functionCode.value.allocatedRegisters[it.reg_dest] == functionCode.value.allocatedRegisters[it.reg_src]
                    }
                    .forEach {
                        output.print("    ")
                        it.writeAsm(output, functionCode.value.allocatedRegisters)
                        output.println()
                    }
                output.println()
            }

            return true
        } catch (_: CompilationFailed) { }

        return false
    }
}
