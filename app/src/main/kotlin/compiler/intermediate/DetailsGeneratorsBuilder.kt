package compiler.intermediate

import compiler.analysis.ProgramAnalyzer
import compiler.ast.AstNode
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Type
import compiler.intermediate.generators.DISPLAY_LABEL_IN_MEMORY
import compiler.intermediate.generators.DefaultFunctionDetailsGenerator
import compiler.intermediate.generators.DefaultGeneratorDetailsGenerator
import compiler.intermediate.generators.ForeignFunctionDetailsGenerator
import compiler.intermediate.generators.ForeignGeneratorDetailsGenerator
import compiler.intermediate.generators.FunctionDetailsGenerator
import compiler.intermediate.generators.GeneratorDetailsGenerator
import compiler.intermediate.generators.VariableLocationType
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf

object DetailsGeneratorsBuilder {
    fun createUniqueIdentifiers(program: Program, allowInconsistentNamingErrors: Boolean): Map<Ref<Function>, UniqueIdentifier> {
        val uniqueIdentifiers = mutableKeyRefMapOf<Function, UniqueIdentifier>()
        val identifierFactory = UniqueIdentifierFactory()
        fun nameFunction(function: Function, pathSoFar: String?): String {
            try {
                uniqueIdentifiers[Ref(function)] = identifierFactory.build(pathSoFar, function.name)
            } catch (error: InconsistentFunctionNamingConvention) {
                // Such errors should occur only as a consequence of skipped NameConflict error in name resolution.
                // Hence, if `allowInconsistentNamingErrors`, which is only set when there were already some errors
                // in diagnostics, we can safely ignore such errors, cause the compilation would be failed later anyway.
                if (!allowInconsistentNamingErrors)
                    throw error
                uniqueIdentifiers[Ref(function)] = UniqueIdentifier("\$INVALID")
            }
            return uniqueIdentifiers[Ref(function)]!!.value
        }

        fun analyze(node: AstNode, pathSoFar: String? = null) {
            when (node) {
                is Program.Global.FunctionDefinition -> {
                    analyze(node.function, pathSoFar)
                }

                is Statement.FunctionDefinition -> {
                    analyze(node.function, pathSoFar)
                }

                is Function -> if (node.isLocal) {
                    val newPrefix = nameFunction(node, pathSoFar)
                    var blockNumber = 0
                    fun handleNestedBlock(statements: List<Statement>) = statements.forEach { analyze(it, newPrefix + "@block" + blockNumber++) }
                    for (statement in node.body) {
                        when (statement) {
                            is Statement.Block -> {
                                handleNestedBlock(statement.block)
                            }

                            is Statement.Conditional -> {
                                handleNestedBlock(statement.actionWhenTrue)
                                handleNestedBlock(statement.actionWhenFalse ?: listOf())
                            }

                            is Statement.Loop -> {
                                handleNestedBlock(statement.action)
                            }

                            else -> analyze(statement, newPrefix)
                        }
                    }
                }

                else -> {}
            }
        }
        program.globals.forEach { analyze(it) }
        return uniqueIdentifiers
    }

    fun createDetailsGenerators(
        program: Program,
        programProperties: ProgramAnalyzer.ProgramProperties,
        allowInconsistentNamingErrors: Boolean = false
    ): Pair<Map<Ref<Function>, FunctionDetailsGenerator>, Map<Ref<Function>, GeneratorDetailsGenerator>> {
        val functionDGs = mutableKeyRefMapOf<Function, FunctionDetailsGenerator>()
        val generatorDGs = mutableKeyRefMapOf<Function, GeneratorDetailsGenerator>()
        val functionIdentifiers = createUniqueIdentifiers(program, allowInconsistentNamingErrors)

        fun createDetailsGenerator(function: Function, depth: ULong) {
            val identifier = when (function.implementation) {
                is Function.Implementation.Foreign -> function.implementation.foreignName
                is Function.Implementation.Local -> functionIdentifiers[Ref(function)]!!.value
            }

            fun calculateVariableLocationTypes(): Map<Ref<NamedNode>, VariableLocationType> {
                val variableLocationTypes = mutableKeyRefMapOf<NamedNode, VariableLocationType>()

                programProperties.variableProperties
                    .filter { (_, properties) -> Ref(properties.owner) == Ref(function) }
                    .forEach { (variable, properties) ->
                        variableLocationTypes[Ref(variable.value as NamedNode)] =
                            if (properties.accessedIn.any { it != Ref(function) } || properties.writtenIn.any { it != Ref(function) })
                                VariableLocationType.MEMORY
                            else
                                VariableLocationType.REGISTER
                    }

                return variableLocationTypes
            }

            val getGDGForNestedLoop = { foreachLoop: Statement.ForeachLoop -> generatorDGs[programProperties.nameResolution[Ref(foreachLoop.generatorCall)]!!]!! }

            if (function.isGenerator) {
                generatorDGs[Ref(function)] = when (function.implementation) {
                    is Function.Implementation.Foreign -> {
                        ForeignGeneratorDetailsGenerator(
                            IFTNode.MemoryLabel(identifier + "_init"),
                            IFTNode.MemoryLabel(identifier + "_resume"),
                            IFTNode.MemoryLabel(identifier + "_finalize")
                        )
                    }

                    is Function.Implementation.Local -> {
                        DefaultGeneratorDetailsGenerator(
                            function.parameters,
                            IFTNode.MemoryLabel(identifier + "_init"),
                            IFTNode.MemoryLabel(identifier + "_resume"),
                            IFTNode.MemoryLabel(identifier + "_finalize"),
                            depth,
                            calculateVariableLocationTypes(),
                            IFTNode.MemoryLabel(DISPLAY_LABEL_IN_MEMORY),
                            programProperties.foreachLoopsInGenerators[Ref(function)]!!,
                            getGDGForNestedLoop
                        )
                    }
                }
            } else {
                functionDGs[Ref(function)] = when (function.implementation) {
                    is Function.Implementation.Foreign -> {
                        ForeignFunctionDetailsGenerator(
                            IFTNode.MemoryLabel(identifier),
                            if (function.returnType !is Type.Unit) 1 else 0
                        )
                    }

                    is Function.Implementation.Local -> {
                        DefaultFunctionDetailsGenerator(
                            function.parameters,
                            programProperties.functionReturnedValueVariables[Ref(function)],
                            IFTNode.MemoryLabel(identifier),
                            depth,
                            calculateVariableLocationTypes(),
                            IFTNode.MemoryLabel(DISPLAY_LABEL_IN_MEMORY)
                        )
                    }
                }
            }
        }

        fun processFunction(function: Function, depth: ULong) {
            createDetailsGenerator(function, depth)

            fun processBlock(block: StatementBlock) {
                for (statement in block) {
                    when (statement) {
                        is Statement.FunctionDefinition -> processFunction(statement.function, depth + 1u)
                        is Statement.Block -> processBlock(statement.block)
                        is Statement.Conditional -> {
                            processBlock(statement.actionWhenTrue)
                            statement.actionWhenFalse?.let { processBlock(it) }
                        }

                        is Statement.Loop -> processBlock(statement.action)
                        else -> {}
                    }
                }
            }

            processBlock(function.body)
        }

        program.globals.forEach { if (it is Program.Global.FunctionDefinition) processFunction(it.function, 0u) }

        return Pair(functionDGs, generatorDGs)
    }
}
