package compiler.analysis

import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf

object GeneratorResolver {
    fun computeGeneratorProperties(ast: Program): Map<Ref<Function>, List<Ref<Statement.ForeachLoop>>> {

        val resultMapping: MutableMap<Ref<Function>, MutableList<Ref<Statement.ForeachLoop>>> = mutableKeyRefMapOf()

        fun process(surroundingGenerators: List<Function>, statement: Statement) {
            fun process(surroundingGenerators: List<Function>, vararg bunchOfBlocks: List<Statement>?) =
                bunchOfBlocks.forEach { block -> block?.forEach { process(surroundingGenerators, it) } }

            when (statement) {
                // Exhaust all possibilities to be forced to update this place when changing the Statement class.
                is Statement.Evaluation -> { }
                is Statement.VariableDefinition -> { }
                is Statement.Assignment -> { }
                is Statement.LoopBreak -> { }
                is Statement.LoopContinuation -> { }
                is Statement.FunctionReturn -> { }
                is Statement.GeneratorYield -> { }

                is Statement.Block -> process(surroundingGenerators, statement.block)
                is Statement.Conditional -> process(surroundingGenerators, statement.actionWhenTrue, statement.actionWhenFalse)
                is Statement.Loop -> process(surroundingGenerators, statement.action)

                is Statement.FunctionDefinition -> {
                    val function = statement.function
                    val updatedSurroundingGenerators = if (function.isGenerator) surroundingGenerators + listOf(function) else surroundingGenerators
                    if (function.isGenerator) resultMapping.putIfAbsent(Ref(function), mutableListOf())
                    process(updatedSurroundingGenerators, function.body)
                }

                is Statement.ForeachLoop -> {
                    surroundingGenerators.forEach { resultMapping[Ref(it)]?.add(Ref(statement)) }
                    process(surroundingGenerators, statement.action)
                }
            }
        }

        fun process(global: Program.Global) {
            when (global) {
                is Program.Global.VariableDefinition -> { }
                is Program.Global.FunctionDefinition -> {
                    val function = global.function
                    val updatedSurroundingGenerators = if (function.isGenerator) listOf(function) else listOf()
                    if (function.isGenerator) resultMapping.putIfAbsent(Ref(function), mutableListOf())
                    function.body.forEach { process(updatedSurroundingGenerators, it) }
                }
            }
        }

        ast.globals.forEach { process(it) }

        return resultMapping
    }
}
