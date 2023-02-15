package compiler.analysis

import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf
import compiler.utils.refSetOf

object FunctionDependenciesAnalyzer {
    fun createCallGraph(ast: Program, nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>): Map<Ref<Function>, Set<Ref<Function>>> {

        val functionCalls = mutableKeyRefMapOf<Function, Set<Ref<Function>>>()

        fun getCalledFunctions(global: Program.Global): Set<Ref<Function>> {
            fun getCalledFunctions(expression: Expression?): Set<Ref<Function>> = when (expression) {
                is Expression.BooleanLiteral -> refSetOf()
                is Expression.NumberLiteral -> refSetOf()
                is Expression.UnitLiteral -> refSetOf()
                is Expression.Variable -> refSetOf()
                null -> refSetOf()

                is Expression.FunctionCall -> refSetOf(nameResolution[Ref(expression)]!!.value as Function) +
                    expression.arguments.map { getCalledFunctions(it.value) }.fold(emptySet(), Set<Ref<Function>>::plus)

                is Expression.UnaryOperation -> getCalledFunctions(expression.operand)

                is Expression.BinaryOperation -> getCalledFunctions(expression.leftOperand) +
                    getCalledFunctions(expression.rightOperand)

                is Expression.Conditional -> getCalledFunctions(expression.condition) +
                    getCalledFunctions(expression.resultWhenTrue) +
                    getCalledFunctions(expression.resultWhenFalse)

                is Expression.ArrayLength -> getCalledFunctions(expression.expression)
                is Expression.ArrayElement -> getCalledFunctions(expression.expression) + getCalledFunctions(expression.index)
                is Expression.ArrayAllocation -> getCalledFunctions(expression.size) + expression.initialization.flatMap { getCalledFunctions(it) }
                is Expression.ArrayGeneration -> getCalledFunctions(expression.generatorCall)
            }

            fun getCalledFunctions(statement: Statement): Set<Ref<Function>> {
                fun getCalledFunctions(list: List<Statement>?): Set<Ref<Function>> =
                    if (list === null) refSetOf() else list.map { getCalledFunctions(it) }.fold(emptySet(), Set<Ref<Function>>::plus)

                return when (statement) {
                    is Statement.LoopBreak -> refSetOf()
                    is Statement.LoopContinuation -> refSetOf()

                    is Statement.FunctionDefinition -> {
                        functionCalls[Ref(statement.function)] = getCalledFunctions(statement.function.body)
                        return statement.function.parameters.map { getCalledFunctions(it.defaultValue) }.fold(emptySet(), Set<Ref<Function>>::plus)
                    }

                    is Statement.Evaluation -> getCalledFunctions(statement.expression)

                    is Statement.VariableDefinition -> getCalledFunctions(statement.variable.value)

                    is Statement.Assignment -> getCalledFunctions(statement.value)

                    is Statement.Block -> getCalledFunctions(statement.block)

                    is Statement.FunctionReturn -> getCalledFunctions(statement.value)

                    is Statement.Conditional -> getCalledFunctions(statement.condition) +
                        getCalledFunctions(statement.actionWhenTrue) +
                        getCalledFunctions(statement.actionWhenFalse)

                    is Statement.Loop -> getCalledFunctions(statement.condition) +
                        getCalledFunctions(statement.action)

                    is Statement.ForeachLoop -> getCalledFunctions(statement.generatorCall) +
                        getCalledFunctions(statement.action)

                    is Statement.GeneratorYield -> getCalledFunctions(statement.value)
                }
            }

            return when (global) {
                is Program.Global.VariableDefinition -> refSetOf()

                is Program.Global.FunctionDefinition -> {
                    functionCalls[Ref(global.function)] = global.function.body.map { getCalledFunctions(it) }.fold(emptySet(), Set<Ref<Function>>::plus)
                    return refSetOf()
                }
            }
        }

        ast.globals.forEach { getCalledFunctions(it) }

        val allFunctions = functionCalls.keys
        var previousPartialTransitiveFunctionCalls: Map<Ref<Function>, Set<Ref<Function>>>
        var nextPartialTransitiveFunctionCalls = functionCalls

        fun getAllCallsOfChildren(calls: Map<Ref<Function>, Set<Ref<Function>>>, function: Function) =
            calls[Ref(function)]!!.map { calls[it]!! }.fold(emptySet(), Set<Ref<Function>>::plus)

        repeat(allFunctions.size) {
            previousPartialTransitiveFunctionCalls = nextPartialTransitiveFunctionCalls
            nextPartialTransitiveFunctionCalls = mutableKeyRefMapOf()
            allFunctions.forEach {
                nextPartialTransitiveFunctionCalls[it] =
                    previousPartialTransitiveFunctionCalls[it]!! +
                    getAllCallsOfChildren(previousPartialTransitiveFunctionCalls, it.value)
            }
        }

        return nextPartialTransitiveFunctionCalls
    }
}
