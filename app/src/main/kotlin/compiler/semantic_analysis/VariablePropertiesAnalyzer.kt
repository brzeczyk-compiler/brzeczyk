package compiler.semantic_analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Program.Global
import compiler.ast.Statement
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostic.VariablePropertiesError.AssignmentToFunctionParameter
import compiler.common.diagnostics.Diagnostic.VariablePropertiesError.AssignmentToOuterVariable
import compiler.common.diagnostics.Diagnostics
import compiler.common.semantic_analysis.MutableReferenceMap
import compiler.common.semantic_analysis.ReferenceHashMap
import compiler.common.semantic_analysis.ReferenceMap

object VariablePropertiesAnalyzer {
    data class VariableProperties(val owner: Function?, val usedInNested: Boolean)

    fun calculateVariableProperties(
        ast: Program,
        nameResolution: ReferenceMap<Any, NamedNode>,
        diagnostics: Diagnostics
    ): ReferenceMap<Any, VariableProperties> {
        val variableProperties: MutableReferenceMap<Any, VariableProperties> = ReferenceHashMap()
        fun resolve(
            // Any = Statement.Assignment | Expression.Variable
            node: Any,
            currentOwner: Function?,
            isAssignment: Boolean
        ) {
            val resolvedVariable: Any = nameResolution.get(node)!!
            // we assume variable is defined before being referenced
            // if this fails then something is wrong with name resolution
            val oldOwner = variableProperties.get(resolvedVariable)!!.owner
            if (resolvedVariable is Function.Parameter && isAssignment) {
                // function parameter must have an owner
                diagnostics.report(AssignmentToFunctionParameter(resolvedVariable, oldOwner!!, currentOwner!!))
            }
            if (oldOwner != currentOwner) {
                // if this was a function that is not nested in the original owner
                // then name resolution would not be able to succeed
                // current owner cannot be null as it is always a function once we start descending
                if (isAssignment)
                    diagnostics.report(AssignmentToOuterVariable(resolvedVariable, oldOwner, currentOwner!!))
                variableProperties.put(resolvedVariable, VariableProperties(oldOwner, true))
            }
        }

        // Any = Expression | Statement
        fun analyzeVariables(node: Any, currentOwner: Function?) {
            when (node) {
                is Statement.Evaluation -> analyzeVariables(node.expression, currentOwner)
                is Statement.VariableDefinition -> analyzeVariables(node.variable, currentOwner)
                is Global.VariableDefinition -> analyzeVariables(node.variable, currentOwner)
                is Statement.FunctionDefinition -> analyzeVariables(node.function, currentOwner)
                is Global.FunctionDefinition -> analyzeVariables(node.function, currentOwner)
                is Statement.Assignment -> {
                    resolve(node, currentOwner, true)
                    analyzeVariables(node.value, currentOwner)
                }
                is Statement.Block -> node.block.forEach { analyzeVariables(it, currentOwner) }
                is Statement.Conditional -> (
                    sequenceOf(node.condition) +
                        node.actionWhenTrue.asSequence() + node.actionWhenFalse.asSequence()
                    ).forEach { analyzeVariables(it, currentOwner) }
                is Statement.Loop -> (sequenceOf(node.condition) + node.action.asSequence())
                    .forEach { analyzeVariables(it, currentOwner) }
                is Statement.FunctionReturn -> analyzeVariables(node.value, currentOwner)
                is Expression.Variable -> {
                    resolve(node, currentOwner, false)
                }
                is Expression.FunctionCall -> node.arguments.forEach { analyzeVariables(it, currentOwner) }
                is Expression.FunctionCall.Argument -> analyzeVariables(node.value, currentOwner)
                is Expression.UnaryOperation -> analyzeVariables(node.operand, currentOwner)
                is Expression.BinaryOperation -> sequenceOf(node.leftOperand, node.rightOperand)
                    .forEach { analyzeVariables(it, currentOwner) }
                is Expression.Conditional -> sequenceOf(node.condition, node.resultWhenTrue, node.resultWhenFalse)
                    .forEach { analyzeVariables(it, currentOwner) }
                is Function -> {
                    node.parameters.forEach {
                        variableProperties.put(it, VariableProperties(node, false))
                        // scope of the inner function has not begun yet
                        it.defaultValue?.let { analyzeVariables(it, currentOwner) }
                    }
                    node.body.forEach { analyzeVariables(it, node) }
                }
                is Variable -> {
                    variableProperties.put(
                        node,
                        VariableProperties(currentOwner, false)
                    )
                    node.value?.let { analyzeVariables(it, currentOwner) }
                }
            }
        }
        ast.globals.forEach { analyzeVariables(it, null) }
        return variableProperties
    }
}
