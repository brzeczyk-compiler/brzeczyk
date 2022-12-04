package compiler.semantic_analysis

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.common.reference_collections.MutableReferenceMap
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.referenceHashMapOf
import java.util.Stack

object NameResolver {
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

        // Auxiliary functions for reporting issues to diagnostics

        fun reportIfNameConflict(name: String, scope: MutableMap<String, NamedNode>) {
            // conflict can only appear in the same scope
            if (scope.containsKey(name))
                diagnostics.report(Diagnostic.NameResolutionError.NameConflict())
        }

        fun reportIfVariableUndefined(variableName: String) {
            // invariant: no node of a particular name exists <==> visibleNames[name] does not exist
            if (!visibleNames.containsKey(variableName))
                diagnostics.report(Diagnostic.NameResolutionError.UndefinedVariable())
        }

        fun reportIfFunctionUndefined(functionName: String) {
            // invariant: no node of a particular name exists <==> visibleNames[name] does not exist
            if (!visibleNames.containsKey(functionName))
                diagnostics.report(Diagnostic.NameResolutionError.UndefinedFunction())
        }

        fun reportIfVariableUsedAsFunction(variableName: String) {
            if (visibleNames[variableName]!!.onlyHasVariables())
                diagnostics.report(Diagnostic.NameResolutionError.VariableIsNotCallable())
        }

        fun reportIfFunctionUsedAsVariable(functionName: String) {
            if (visibleNames[functionName]!!.onlyHasFunctions())
                diagnostics.report(Diagnostic.NameResolutionError.FunctionIsNotVariable())
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

                    // we have to be able to map "napisz(...)" FunctionCalls to something
                    // TODO: napiszNode has to actually have some meaning
                    val dummyNapiszNode: Function = Function(
                        "napisz",
                        listOf(Function.Parameter("wartość", Type.Number, null)),
                        Type.Unit,
                        listOf()
                    )
                    addName("napisz", dummyNapiszNode, newScope)

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
                    reportIfNameConflict(node.name, currentScope)

                    // first analyze the value, then add name, because we can't have self-referencing definitions
                    node.value?.let { analyzeNode(it, currentScope) }
                    addName(node.name, node, currentScope)
                }

                is Function -> {
                    // first analyze each parameter, so they can't refer to each other and to the function
                    node.parameters.forEach { analyzeNode(it, currentScope) }

                    reportIfNameConflict(node.name, currentScope)

                    // first add name, then create scope and analyze body because we can have recursive calls
                    addName(node.name, node, currentScope)
                    val newScope = makeScope() // function introduces a new scope of names

                    for (param in node.parameters) { // and then add their names
                        reportIfNameConflict(param.name, newScope) // verifies that the parameters have different names
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
                    reportIfVariableUndefined(node.name)
                    reportIfFunctionUsedAsVariable(node.name)
                    nameDefinitions[node] = visibleNames[node.name]!!.topVariable()
                }

                is Expression.FunctionCall -> {
                    reportIfFunctionUndefined(node.name)
                    reportIfVariableUsedAsFunction(node.name)
                    nameDefinitions[node] = visibleNames[node.name]!!.topFunction()
                    node.arguments.forEach { analyzeNode(it, currentScope) }
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
                    reportIfVariableUndefined(node.variableName)
                    reportIfFunctionUsedAsVariable(node.variableName) // when we try to assign to a function
                    nameDefinitions[node] = visibleNames[node.variableName]!!.topVariable()
                    analyzeNode(node.value, currentScope)
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
        return nameDefinitions
    }
}
