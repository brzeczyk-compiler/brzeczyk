package compiler.analysis

import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Function
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf

object GeneratorResolver {
    fun computeGeneratorProperties(ast: Program): Map<Ref<Function>, List<Ref<Statement.ForeachLoop>>> {

        val resultMapping: MutableMap<Ref<Function>, MutableList<Ref<Statement.ForeachLoop>>> = mutableKeyRefMapOf()

        fun process(parentGenerators: List<Function>, statement: Statement) {
            fun process(parentGenerators: List<Function>, vararg bunchOfBlocks: List<Statement>?) =
                bunchOfBlocks.toList().forEach { block -> block?.forEach { process(parentGenerators, it) } }

            when (statement) {
                // Exhaust all possibilities to be forced to update this place when changing the Statement class.
                is Statement.Evaluation -> { }
                is Statement.VariableDefinition -> { }
                is Statement.Assignment -> { }
                is Statement.LoopBreak -> { }
                is Statement.LoopContinuation -> { }
                is Statement.FunctionReturn -> { }
                is Statement.GeneratorYield -> { }

                is Statement.Block -> process(parentGenerators, statement.block)
                is Statement.Conditional -> process(parentGenerators, statement.actionWhenTrue, statement.actionWhenFalse)
                is Statement.Loop -> process(parentGenerators, statement.action)

                is Statement.FunctionDefinition -> {
                    val function = statement.function
                    val updatedParentGenerators = if (function.isGenerator) parentGenerators + listOf(function) else parentGenerators
                    if(function.isGenerator) resultMapping.putIfAbsent(Ref(function), mutableListOf())
                    process(updatedParentGenerators, function.body)
                }

                is Statement.ForeachLoop -> {
                    parentGenerators.forEach { resultMapping[Ref(it)]?.add(Ref(statement)) }
                    process(parentGenerators, statement.action)
                }

            }
        }

        fun process(global: Program.Global) {
            when (global) {
                is Program.Global.VariableDefinition -> { }
                is Program.Global.FunctionDefinition -> {
                    val function = global.function
                    val updatedParentGenerators = if (function.isGenerator) listOf(function) else listOf()
                    if(function.isGenerator) resultMapping.putIfAbsent(Ref(function), mutableListOf())
                    function.body.forEach { process(updatedParentGenerators, it) }
                }
            }
        }

        ast.globals.forEach { process(it) }

        return resultMapping
    }
}