package compiler.semantic_analysis

import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceMap

object DefaultParameterResolver {

    fun mapFunctionParametersToDummyVariables(ast: Program): ReferenceMap<Function.Parameter, Variable> {
        val resultMapping: ReferenceHashMap<Function.Parameter, Variable> = ReferenceHashMap()

        fun process(global: Program.Global) {
            fun process(statement: Statement) {
                fun process(vararg bunchOfBlocks: List<Statement>?) = bunchOfBlocks.toList().forEach { block -> block?.forEach { process(it) } }

                when (statement) {
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
                                resultMapping[it] = Variable(
                                    Variable.Kind.VALUE,
                                    "_dummy_${it.name}",
                                    it.type,
                                    it.defaultValue
                                )
                            }
                        }
                        process(statement.function.body)
                    }
                }
            }

            when (global) {
                is Program.Global.VariableDefinition -> { }
                is Program.Global.FunctionDefinition -> {
                    global.function.parameters.forEach {
                        if (it.defaultValue != null) {
                            resultMapping[it] = Variable(
                                Variable.Kind.CONSTANT,
                                "_dummy_${it.name}",
                                it.type,
                                it.defaultValue
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
