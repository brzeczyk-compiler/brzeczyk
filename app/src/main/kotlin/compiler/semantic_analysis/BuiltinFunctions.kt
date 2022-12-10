package compiler.semantic_analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceSet
import compiler.common.reference_collections.referenceHashSetOf

val functionNapisz = Function(
    "napisz",
    listOf(Function.Parameter("wartość", Type.Number, null)),
    Type.Unit,
    listOf()
)

val builtinFunctionsByName = mapOf(functionNapisz.name to functionNapisz)

fun getUsedBuiltinFunctions(program: Program): ReferenceSet<Function> {
    val result = referenceHashSetOf<Function>()

    fun analyzeNode(node: Any) {
        when (node) {
            is Program -> { node.globals.forEach { analyzeNode(it) } }
            is Program.Global.VariableDefinition -> { analyzeNode(node.variable) }
            is Program.Global.FunctionDefinition -> { analyzeNode(node.function) }
            is Variable -> { node.value?.let { analyzeNode(it) } }
            is Function -> {
                node.parameters.forEach { analyzeNode(it) }
                node.body.forEach { analyzeNode(it) }
            }
            is Function.Parameter -> { node.defaultValue?.let { analyzeNode(it) } }

            // Expressions
            is Expression.Variable -> {}
            is Expression.FunctionCall -> {
                if (builtinFunctionsByName.containsKey(node.name))
                    result.add(builtinFunctionsByName[node.name]!!)
                node.arguments.forEach { analyzeNode(it) }
            }
            is Expression.FunctionCall.Argument -> { analyzeNode(node.value) }
            is Expression.UnaryOperation -> { analyzeNode(node.operand) }
            is Expression.BinaryOperation -> {
                analyzeNode(node.leftOperand)
                analyzeNode(node.rightOperand)
            }
            is Expression.Conditional -> {
                analyzeNode(node.condition)
                analyzeNode(node.resultWhenTrue)
                analyzeNode(node.resultWhenFalse)
            }

            // Statements
            is Statement.Evaluation -> { analyzeNode(node.expression) }
            is Statement.VariableDefinition -> { analyzeNode(node.variable) }
            is Statement.FunctionDefinition -> { analyzeNode(node.function) }
            is Statement.Assignment -> { analyzeNode(node.value) }
            is Statement.Block -> { node.block.forEach { analyzeNode(it) } }
            is Statement.Conditional -> {
                analyzeNode(node.condition)
                node.actionWhenTrue.forEach { analyzeNode(it) }
                node.actionWhenFalse?.forEach { analyzeNode(it) }
            }
            is Statement.Loop -> {
                analyzeNode(node.condition)
                node.action.forEach { analyzeNode(it) }
            }
            is Statement.FunctionReturn -> { analyzeNode(node.value) }
        }
    }
    analyzeNode(program)
    return result
}
