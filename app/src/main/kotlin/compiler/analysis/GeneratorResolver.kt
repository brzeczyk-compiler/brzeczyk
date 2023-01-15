package compiler.analysis

import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Variable
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf

object GeneratorResolver {
    fun computeGeneratorProperties(ast: Program): Map<Ref<Function>, List<Ref<Statement.ForeachLoop>>> {

        val resultMapping: MutableMap<Ref<Function>, MutableList<Ref<Statement.ForeachLoop>>> = mutableKeyRefMapOf()

        fun process(surroundingGenerators: List<Function>, node: AstNode) {
            fun process(surroundingGenerators: List<Function>, vararg bunchOfBlocks: List<Statement>?) =
                bunchOfBlocks.forEach { block -> block?.forEach { process(surroundingGenerators, it) } }

            fun processFunction(surroundingGenerators: List<Function>, function: Function) {
                val updatedSurroundingGenerators = if (function.isGenerator) surroundingGenerators + listOf(function) else surroundingGenerators
                if (function.isGenerator) resultMapping.putIfAbsent(Ref(function), mutableListOf())
                process(updatedSurroundingGenerators, function.body)
            }

            fun processForEachLoop(surroundingGenerators: List<Function>, foreachLoop: Statement.ForeachLoop) {
                surroundingGenerators.forEach { resultMapping[Ref(it)]?.add(Ref(foreachLoop)) }
                process(surroundingGenerators, foreachLoop.action)
            }

            when (node) {
                is Program -> { }
                is Expression -> { }
                is Variable -> { }
                is Program.Global.VariableDefinition -> { }
                is Expression.FunctionCall.Argument -> { }
                is Function -> { }
                is Function.Parameter -> { }

                is Program.Global.FunctionDefinition -> processFunction(surroundingGenerators, node.function)

                is Statement -> when (node) {
                    is Statement.Evaluation -> { }
                    is Statement.VariableDefinition -> { }
                    is Statement.Assignment -> { }
                    is Statement.LoopBreak -> { }
                    is Statement.LoopContinuation -> { }
                    is Statement.FunctionReturn -> { }
                    is Statement.GeneratorYield -> { }

                    is Statement.Block -> process(surroundingGenerators, node.block)
                    is Statement.Conditional -> process(surroundingGenerators, node.actionWhenTrue, node.actionWhenFalse)
                    is Statement.Loop -> process(surroundingGenerators, node.action)

                    is Statement.FunctionDefinition -> processFunction(surroundingGenerators, node.function)
                    is Statement.ForeachLoop -> processForEachLoop(surroundingGenerators, node)
                }
            }
        }

        ast.globals.forEach { process(listOf(), it) }

        return resultMapping
    }
}
