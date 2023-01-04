package compiler.analysis

import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf

object ReturnValueVariableCreator {

    fun createDummyVariablesForFunctionReturnValue(ast: Program): Map<Ref<Function>, Variable> {
        val resultMapping: MutableMap<Ref<Function>, Variable> = mutableKeyRefMapOf()

        fun createReturnVariableFor(function: Function) {
            if (function.implementation is Function.Implementation.Local && function.returnType !is Type.Unit)
                resultMapping[Ref(function)] = Variable(
                    Variable.Kind.VALUE,
                    "_result_dummy_${function.name}",
                    function.returnType,
                    null,
                    null,
                )
        }

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
                    createReturnVariableFor(statement.function)
                    process(statement.function.body)
                }

                is Statement.ForeachLoop -> TODO()
            }
        }

        ast.globals.filterIsInstance<Program.Global.FunctionDefinition>().forEach { globalFunction ->
            createReturnVariableFor(globalFunction.function)
            globalFunction.function.body.forEach { process(it) }
        }

        return resultMapping
    }
}
