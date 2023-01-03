package compiler.analysis

import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Variable
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf

object DefaultParameterResolver {

    fun mapFunctionParametersToDummyVariables(ast: Program): Map<Ref<Function.Parameter>, Variable> {
        val resultMapping: MutableMap<Ref<Function.Parameter>, Variable> = mutableKeyRefMapOf()

        fun process(statement: Statement) {
            fun process(vararg bunchOfBlocks: List<Statement>?) = bunchOfBlocks.toList().forEach { block -> block?.forEach { process(it) } }

            when (statement) {
                // Exhaust all possibilities to be forced to update this place when changing the Statement class.
                is Statement.Evaluation -> { }
                is Statement.VariableDefinition -> { }
                is Statement.Assignment -> { }
                is Statement.LoopBreak -> { }
                is Statement.LoopContinuation -> { }
                is Statement.FunctionReturn -> { }

                is Statement.Block -> process(statement.block)
                is Statement.Conditional -> process(statement.actionWhenTrue, statement.actionWhenFalse)
                is Statement.Loop -> process(statement.action)

                is Statement.FunctionDefinition -> {
                    statement.function.parameters.forEach {
                        if (it.defaultValue != null) {
                            resultMapping[Ref(it)] = Variable(
                                Variable.Kind.VALUE,
                                "_dummy_${it.name}",
                                it.type,
                                it.defaultValue,
                                null,
                            )
                        }
                    }
                    process(statement.function.body)
                }
            }
        }

        fun process(global: Program.Global) {
            when (global) {
                is Program.Global.VariableDefinition -> { }
                is Program.Global.FunctionDefinition -> {
                    global.function.parameters.forEach {
                        if (it.defaultValue != null) {
                            resultMapping[Ref(it)] = Variable(
                                Variable.Kind.CONSTANT,
                                "_dummy_${it.name}",
                                it.type,
                                it.defaultValue,
                                null,
                            )
                        }
                    }
                    global.function.body.forEach { process(it) }
                }
            }
        }

        ast.globals.forEach { process(it) }

        return resultMapping
    }
}
