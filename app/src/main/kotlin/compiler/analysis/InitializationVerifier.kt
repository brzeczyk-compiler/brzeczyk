package compiler.analysis

import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Program.Global
import compiler.ast.Statement
import compiler.ast.Variable
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.VariableInitializationError.ReferenceToUninitializedVariable
import compiler.diagnostics.Diagnostics
import compiler.utils.Ref
import compiler.utils.mutableRefSetOf

object InitializationVerifier {
    fun verifyAccessedVariablesAreInitialized(
        ast: Program,
        nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>,
        defaultParameterMapping: Map<Ref<Function.Parameter>, Variable>,
        diagnostics: Diagnostics
    ) {
        fun verifyInitialization(
            node: AstNode,
            initializedVariables: MutableSet<Ref<NamedNode>>,
            traversedFunctions: MutableSet<Ref<Function>> // so that we don't analyze calls of the same function multiple times
        ) {
            when (node) {
                is Statement.Evaluation -> verifyInitialization(node.expression, initializedVariables, traversedFunctions)
                is Statement.VariableDefinition -> verifyInitialization(node.variable, initializedVariables, traversedFunctions)
                is Global.VariableDefinition -> verifyInitialization(node.variable, initializedVariables, traversedFunctions)
                is Statement.FunctionDefinition -> {
                    verifyInitialization(node.function, initializedVariables, traversedFunctions)
                }
                is Global.FunctionDefinition -> verifyInitialization(node.function, initializedVariables, traversedFunctions)
                is Statement.Assignment -> {
                    verifyInitialization(node.value, initializedVariables, traversedFunctions)
                    // the variable will be initialized after assignment, not before
                    initializedVariables.add(nameResolution[Ref(node)]!!)
                }
                is Statement.Block -> {
                    node.block.forEach { verifyInitialization(it, initializedVariables, traversedFunctions) }
                }
                is Statement.Conditional -> {
                    verifyInitialization(node.condition, initializedVariables, traversedFunctions)
                    val initializedVariablesWhenTrue = initializedVariables.toMutableSet()
                    val traversedFunctionsWhenTrue = traversedFunctions.toMutableSet()
                    node.actionWhenTrue.forEach {
                        verifyInitialization(
                            it, initializedVariablesWhenTrue,
                            traversedFunctionsWhenTrue
                        )
                    }

                    val initializedVariablesWhenFalse = initializedVariables.toMutableSet()
                    val traversedFunctionsWhenFalse = traversedFunctions.toMutableSet()
                    node.actionWhenFalse?.forEach {
                        verifyInitialization(
                            it, initializedVariablesWhenFalse,
                            traversedFunctionsWhenFalse
                        )
                    }

                    val initializedVariablesWhenBoth = initializedVariablesWhenTrue intersect initializedVariablesWhenFalse
                    val traversedFunctionsWhenBoth = traversedFunctionsWhenTrue intersect traversedFunctionsWhenFalse
                    initializedVariables.clear()
                    initializedVariables.addAll(initializedVariablesWhenBoth)
                    traversedFunctions.clear()
                    traversedFunctions.addAll(traversedFunctionsWhenBoth)
                }
                is Statement.Loop -> {
                    verifyInitialization(node.condition, initializedVariables, traversedFunctions)
                    val initializedVariablesInBody = initializedVariables.toMutableSet()
                    val traversedFunctionsInBody = traversedFunctions.toMutableSet()
                    // loop body might never execute
                    node.action.forEach {
                        verifyInitialization(
                            it, initializedVariablesInBody,
                            traversedFunctionsInBody
                        )
                    }
                }
                is Statement.FunctionReturn -> {
                    verifyInitialization(node.value, initializedVariables, traversedFunctions)
                }
                is Expression.Variable -> {
                    val resolvedVariable: NamedNode = nameResolution[Ref(node)]!!.value
                    if (Ref(resolvedVariable) !in initializedVariables) {
                        diagnostics.report(ReferenceToUninitializedVariable(resolvedVariable))
                    }
                }
                is Expression.FunctionCall -> {
                    node.arguments.forEach { verifyInitialization(it, initializedVariables, traversedFunctions) }
                    val resolvedFunction: Function = nameResolution[Ref(node)]!!.value as Function
                    if (Ref(resolvedFunction) !in traversedFunctions) {
                        traversedFunctions.add(Ref(resolvedFunction))
                        resolvedFunction.body.forEach { verifyInitialization(it, initializedVariables, traversedFunctions) }
                    }
                }
                is Expression.FunctionCall.Argument -> verifyInitialization(node.value, initializedVariables, traversedFunctions)
                is Expression.UnaryOperation -> verifyInitialization(node.operand, initializedVariables, traversedFunctions)
                is Expression.BinaryOperation -> sequenceOf(node.leftOperand, node.rightOperand)
                    .forEach { verifyInitialization(it, initializedVariables, traversedFunctions) }
                is Expression.Conditional -> {
                    verifyInitialization(node.condition, initializedVariables, traversedFunctions)
                    val initializedVariablesWhenTrue = initializedVariables.toMutableSet()
                    val traversedFunctionsWhenTrue = traversedFunctions.toMutableSet()
                    verifyInitialization(node.resultWhenTrue, initializedVariablesWhenTrue, traversedFunctionsWhenTrue)

                    val initializedVariablesWhenFalse = initializedVariables.toMutableSet()
                    val traversedFunctionsWhenFalse = traversedFunctions.toMutableSet()
                    verifyInitialization(node.resultWhenFalse, initializedVariablesWhenFalse, traversedFunctionsWhenFalse)

                    val initializedVariablesWhenBoth = initializedVariablesWhenTrue intersect initializedVariablesWhenFalse
                    val traversedFunctionsWhenBoth = traversedFunctionsWhenTrue intersect traversedFunctionsWhenFalse
                    initializedVariables.clear()
                    initializedVariables.addAll(initializedVariablesWhenBoth)
                    traversedFunctions.clear()
                    traversedFunctions.addAll(traversedFunctionsWhenBoth)
                }
                is Function -> {
                    node.parameters.forEach { verifyInitialization(it, initializedVariables, traversedFunctions) }
                }
                is Function.Parameter -> {
                    initializedVariables.add(Ref(node))
                    defaultParameterMapping[Ref(node)]?.let { initializedVariables.add(Ref(it)) }
                    node.defaultValue?.let { verifyInitialization(it, initializedVariables, traversedFunctions) }
                }
                is Variable -> {
                    if (node.value != null) {
                        verifyInitialization(node.value, initializedVariables, traversedFunctions)
                        initializedVariables.add(Ref(node))
                    }
                }
                else -> {}
            }
        }
        val initializedVariables: MutableSet<Ref<NamedNode>> = mutableRefSetOf()
        val traversedFunctions: MutableSet<Ref<Function>> = mutableRefSetOf()
        ast.globals.forEach { verifyInitialization(it, initializedVariables, traversedFunctions) }
        // we now need to simulate the execution of top level functions to verify their correctness
        ast.globals.filterIsInstance<Global.FunctionDefinition>().forEach {
            val initializedVariablesLocal = initializedVariables.toMutableSet()
            val traversedFunctionsLocal = traversedFunctions.toMutableSet()
            // don't traverse this function, we're doing this now
            traversedFunctionsLocal.add(Ref(it.function))
            it.function.body.forEach { instruction ->
                verifyInitialization(
                    instruction,
                    initializedVariablesLocal, traversedFunctionsLocal
                )
            }
        }
    }
}