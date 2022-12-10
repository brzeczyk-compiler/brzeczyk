package compiler.semantic_analysis

import compiler.Compiler.CompilationFailed
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.common.reference_collections.MutableReferenceMap
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.referenceHashMapOf
import java.util.Stack

object NameResolver {
    class ResolutionFailed : CompilationFailed()

    class NameOverloadState {
        /*
        For each name we keep a NameOverloadState which consists of stacks of entities of a certain NamedNode type.
        The stack structure represents name overloading. The top of each stack represents the node of a particular type
        which has most recently overloaded the name.
         */
        private val variables: Stack<NamedNode> = Stack() // will only contain Variable and Function.Parameter
        private val functions: Stack<Function> = Stack()

        fun addVariable(variable: NamedNode) = variables.add(variable)
        fun addFunction(function: Function) = functions.add(function)

        fun topVariable(): NamedNode = variables.peek()
        fun topFunction(): Function = functions.peek()

        fun popVariable(): NamedNode = variables.pop()
        fun popFunction(): Function = functions.pop()

        fun isEmpty(): Boolean = variables.isEmpty() && functions.isEmpty()
        fun onlyHasVariables(): Boolean = variables.isNotEmpty() && functions.isEmpty()
        fun onlyHasFunctions(): Boolean = functions.isNotEmpty() && variables.isEmpty()
    }

    fun calculateNameResolution(ast: Program, diagnostics: Diagnostics): ReferenceMap<Any, NamedNode> {

        val nameDefinitions: MutableReferenceMap<Any, NamedNode> = referenceHashMapOf()

        val visibleNames: MutableMap<String, NameOverloadState> = HashMap()

        var failed = false

        // Auxiliary functions for reporting issues to diagnostics

        fun reportIfNameConflict(namedNode: NamedNode, scope: MutableMap<String, NamedNode>) {
            // conflict can only appear in the same scope
            if (scope.containsKey(namedNode.name)) {
                diagnostics.report(
                    Diagnostic.ResolutionDiagnostic.NameResolutionError.NameConflict(
                        scope[namedNode.name]!!, namedNode,
                        withBuiltinFunction = builtinFunctionsByName.containsKey(namedNode.name)
                    )
                )
            }
        }

        fun checkVariableUsage(name: String, astNode: AstNode): Boolean {
            if (!visibleNames.containsKey(name)) {
                if (astNode is Expression.Variable)
                    diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.UndefinedVariable(astNode))
                if (astNode is Statement.Assignment)
                    diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.AssignmentToUndefinedVariable(astNode))
            } else if (visibleNames[name]!!.onlyHasFunctions()) {
                if (astNode is Expression.Variable)
                    diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.FunctionIsNotVariable(visibleNames[name]!!.topFunction(), astNode))
                if (astNode is Statement.Assignment)
                    diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.AssignmentToFunction(visibleNames[name]!!.topFunction(), astNode))
            } else {
                return false
            }
            failed = true
            return true
        }

        fun checkFunctionUsage(functionCall: Expression.FunctionCall): Boolean {
            if (!visibleNames.containsKey(functionCall.name)) {
                diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.UndefinedFunction(functionCall))
            } else if (visibleNames[functionCall.name]!!.onlyHasVariables()) {
                diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.VariableIsNotCallable(visibleNames[functionCall.name]!!.topVariable(), functionCall))
            } else {
                return false
            }
            failed = true
            return true
        }

        // Auxiliary functions for managing scopes of names

        fun makeScope(): MutableMap<String, NamedNode> {
            return HashMap<String, NamedNode>().toMutableMap()
        }

        fun addName(name: String, node: NamedNode, scope: MutableMap<String, NamedNode>) {
            scope[name] = node
            if (!visibleNames.containsKey(name))
                visibleNames[name] = NameOverloadState()
            if (node is Variable || node is Function.Parameter)
                visibleNames[name]!!.addVariable(node)
            if (node is Function)
                visibleNames[name]!!.addFunction(node)
        }

        fun destroyScope(scope: MutableMap<String, NamedNode>) {
            for ((name, node) in scope) {
                // if (name, node) exists in scope then the node has to be on top of the corresponding NameOverloadState stack
                if (node is Variable || node is Function.Parameter)
                    visibleNames[name]!!.popVariable()
                if (node is Function)
                    visibleNames[name]!!.popFunction()

                // invariant: no node of a particular name exists <==> visibleNames[name] does not exist
                if (visibleNames[name]!!.isEmpty())
                    visibleNames.remove(name)
            }
        }

        // Core function

        fun analyzeNode(
            node: Any,
            currentScope: MutableMap<String, NamedNode>
        ) {

            when (node) {

                is Program -> {
                    val newScope = makeScope()

                    for ((name, function) in builtinFunctionsByName.entries)
                        addName(name, function, newScope)

                    node.globals.forEach { analyzeNode(it, newScope) }
                    destroyScope(newScope)
                }

                is Program.Global.VariableDefinition -> {
                    analyzeNode(node.variable, currentScope)
                }

                is Program.Global.FunctionDefinition -> {
                    analyzeNode(node.function, currentScope)
                }

                is Variable -> {
                    reportIfNameConflict(node, currentScope)

                    // first analyze the value, then add name, because we can't have self-referencing definitions
                    node.value?.let { analyzeNode(it, currentScope) }
                    addName(node.name, node, currentScope)
                }

                is Function -> {
                    // first analyze each parameter, so they can't refer to each other and to the function
                    node.parameters.forEach { analyzeNode(it, currentScope) }

                    reportIfNameConflict(node, currentScope)

                    // first add name, then create scope and analyze body because we can have recursive calls
                    addName(node.name, node, currentScope)
                    val newScope = makeScope() // function introduces a new scope of names

                    for (param in node.parameters) { // and then add their names
                        reportIfNameConflict(param, newScope) // verifies that the parameters have different names
                        addName(param.name, param, newScope)
                    }

                    node.body.forEach { analyzeNode(it, newScope) }
                    destroyScope(newScope) // forget about parameters and names introduced in the body
                }

                is Function.Parameter -> {
                    node.defaultValue?.let { analyzeNode(it, currentScope) }
                }

                // Expressions

                is Expression.Variable -> {
                    if (!checkVariableUsage(node.name, node))
                        nameDefinitions[node] = visibleNames[node.name]!!.topVariable()
                }

                is Expression.FunctionCall -> {
                    if (!checkFunctionUsage(node)) {
                        nameDefinitions[node] = visibleNames[node.name]!!.topFunction()
                        node.arguments.forEach { analyzeNode(it, currentScope) }
                    }
                }

                is Expression.FunctionCall.Argument -> {
                    analyzeNode(node.value, currentScope)
                }

                is Expression.UnaryOperation -> {
                    analyzeNode(node.operand, currentScope)
                }

                is Expression.BinaryOperation -> {
                    analyzeNode(node.leftOperand, currentScope)
                    analyzeNode(node.rightOperand, currentScope)
                }

                is Expression.Conditional -> {
                    analyzeNode(node.condition, currentScope)
                    analyzeNode(node.resultWhenTrue, currentScope)
                    analyzeNode(node.resultWhenFalse, currentScope)
                }

                // Statements

                is Statement.Evaluation -> {
                    analyzeNode(node.expression, currentScope)
                }

                is Statement.VariableDefinition -> {
                    analyzeNode(node.variable, currentScope)
                }

                is Statement.FunctionDefinition -> {
                    analyzeNode(node.function, currentScope)
                }

                is Statement.Assignment -> {
                    if (!checkVariableUsage(node.variableName, node)) {
                        nameDefinitions[node] = visibleNames[node.variableName]!!.topVariable()
                        analyzeNode(node.value, currentScope)
                    }
                }

                is Statement.Block -> {
                    val newScope = makeScope() // block introduces a new scope of names
                    node.block.forEach { analyzeNode(it, newScope) }
                    destroyScope(newScope)
                }

                is Statement.Conditional -> {
                    analyzeNode(node.condition, currentScope)

                    val firstScope = makeScope() // conditional blocks both introduce new scopes
                    node.actionWhenTrue.forEach { analyzeNode(it, firstScope) }
                    destroyScope(firstScope)

                    val secondScope = makeScope()
                    node.actionWhenFalse?.forEach { analyzeNode(it, secondScope) }
                    destroyScope(secondScope)
                }

                is Statement.Loop -> {
                    analyzeNode(node.condition, currentScope)

                    val newScope = makeScope() // loop body introduces a new scope
                    node.action.forEach { analyzeNode(it, newScope) }
                    destroyScope(newScope)
                }

                is Statement.FunctionReturn -> {
                    analyzeNode(node.value, currentScope)
                }
            }
        }

        analyzeNode(ast, makeScope())

        if (failed)
            throw ResolutionFailed()

        return nameDefinitions
    }
}
