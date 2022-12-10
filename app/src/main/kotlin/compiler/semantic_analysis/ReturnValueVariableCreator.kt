package compiler.semantic_analysis

import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.referenceHashMapOf

object ReturnValueVariableCreator {

    fun createDummyVariablesForFunctionReturnValue(ast: Program): ReferenceMap<Function, Variable> {
        val resultMapping: ReferenceHashMap<Function, Variable> = referenceHashMapOf()

        fun createReturnVariableFor(function: Function) {
            if (function.returnType !is Type.Unit)
                resultMapping[function] = Variable(
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
            }
        }

        ast.globals.filterIsInstance<Program.Global.FunctionDefinition>().forEach { globalFunction ->
            createReturnVariableFor(globalFunction.function)
            globalFunction.function.body.forEach { process(it) }
        }

        return resultMapping
    }
}
