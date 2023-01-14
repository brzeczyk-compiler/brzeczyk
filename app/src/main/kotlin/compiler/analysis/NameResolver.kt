package compiler.analysis

import compiler.Compiler.CompilationFailed
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Variable
import compiler.diagnostics.Diagnostic
import compiler.diagnostics.Diagnostics
import compiler.utils.Ref
import compiler.utils.mutableRefMapOf
import java.util.Stack
import kotlin.collections.HashMap
import kotlin.math.max

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
        private val generators: Stack<Function> = Stack()

        fun addVariable(variable: NamedNode) = variables.add(variable)
        fun addFunction(function: Function) = functions.add(function)
        fun addGenerator(function: Function) = generators.add(function)

        fun topVariable(): NamedNode = variables.peek()
        fun topFunction(): Function = functions.peek()
        fun topGenerator(): Function = generators.peek()

        fun popVariable(): NamedNode = variables.pop()
        fun popFunction(): Function = functions.pop()
        fun popGenerator(): Function = generators.pop()

        fun isEmpty(): Boolean = variables.isEmpty() && functions.isEmpty()

        fun hasVariable(): Boolean = variables.isNotEmpty()
        fun hasFunction(): Boolean = functions.isNotEmpty()
        fun hasGenerator(): Boolean = generators.isNotEmpty()
    }

    data class Result(val nameDefinitions: Map<Ref<AstNode>, Ref<NamedNode>>, val programStaticDepth: Int)

    fun calculateNameResolution(ast: Program, diagnostics: Diagnostics): Result {

        val nameDefinitions: MutableMap<Ref<AstNode>, Ref<NamedNode>> = mutableRefMapOf()

        val visibleNames: MutableMap<String, NameOverloadState> = HashMap()

        var failed = false

        // Auxiliary functions for reporting issues to diagnostics

        fun reportIfNameConflict(namedNode: NamedNode, scope: MutableMap<String, NamedNode>) {
            // conflict can only appear in the same scope
            if (scope.containsKey(namedNode.name)) {
                diagnostics.report(
                    Diagnostic.ResolutionDiagnostic.NameResolutionError.NameConflict(scope[namedNode.name]!!, namedNode)
                )
            }
        }

        fun checkVariableUsage(name: String, astNode: AstNode): Boolean {
            if (!visibleNames.containsKey(name)) {
                if (astNode is Expression.Variable)
                    diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.UndefinedVariable(astNode))
                if (astNode is Statement.Assignment)
                    diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.AssignmentToUndefinedVariable(astNode))
            } else if (!visibleNames[name]!!.hasVariable()) { // then it must have at least one function or generator
                if (astNode is Expression.Variable) {
                    diagnostics.report(
                        Diagnostic.ResolutionDiagnostic.NameResolutionError.CallableIsNotVariable(
                            if (visibleNames[name]!!.hasFunction())
                                visibleNames[name]!!.topFunction()
                            else
                                visibleNames[name]!!.topGenerator(),
                            astNode
                        )
                    )
                }
                if (astNode is Statement.Assignment) {
                    diagnostics.report(
                        Diagnostic.ResolutionDiagnostic.NameResolutionError.AssignmentToCallable(
                            if (visibleNames[name]!!.hasFunction())
                                visibleNames[name]!!.topFunction()
                            else
                                visibleNames[name]!!.topGenerator(),
                            astNode
                        )
                    )
                }
            } else {
                return false
            }
            failed = true
            return true
        }

        fun checkFunctionUsage(functionCall: Expression.FunctionCall): Boolean {
            if (!visibleNames.containsKey(functionCall.name)) {
                diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.UndefinedFunction(functionCall))
            } else if (!visibleNames[functionCall.name]!!.hasFunction()) {
                if (visibleNames[functionCall.name]!!.hasGenerator())
                    diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.GeneratorUsedAsFunction(visibleNames[functionCall.name]!!.topGenerator(), functionCall))
                else if (visibleNames[functionCall.name]!!.hasVariable())
                    diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.VariableIsNotCallable(visibleNames[functionCall.name]!!.topVariable(), functionCall))
            } else {
                return false
            }
            failed = true
            return true
        }

        fun checkGeneratorUsage(generatorCall: Expression.FunctionCall): Boolean {
            if (!visibleNames.containsKey(generatorCall.name)) {
                diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.UndefinedFunction(generatorCall))
            } else if (!visibleNames[generatorCall.name]!!.hasGenerator()) {
                if (visibleNames[generatorCall.name]!!.hasFunction())
                    diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.FunctionUsedAsAGenerator(visibleNames[generatorCall.name]!!.topFunction(), generatorCall))
                else if (visibleNames[generatorCall.name]!!.hasVariable())
                    diagnostics.report(Diagnostic.ResolutionDiagnostic.NameResolutionError.VariableIsNotCallable(visibleNames[generatorCall.name]!!.topVariable(), generatorCall))
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
            else if (node is Function && !node.isGenerator)
                visibleNames[name]!!.addFunction(node)
            else if (node is Function)
                visibleNames[name]!!.addGenerator(node)
        }

        fun destroyScope(scope: MutableMap<String, NamedNode>) {
            for ((name, node) in scope) {
                // if (name, node) exists in scope then the node has to be on top of the corresponding NameOverloadState stack
                if (node is Variable || node is Function.Parameter)
                    visibleNames[name]!!.popVariable()
                else if (node is Function && !node.isGenerator)
                    visibleNames[name]!!.popFunction()
                else if (node is Function)
                    visibleNames[name]!!.popGenerator()

                // invariant: no node of a particular name exists <==> visibleNames[name] does not exist
                if (visibleNames[name]!!.isEmpty())
                    visibleNames.remove(name)
            }
        }

        // Additionally compute static function depth of the program
        var currentStaticDepth: Int = 0
        var maxStaticDepth: Int = 0

        // Core function

        fun analyzeNode(
            node: AstNode,
            currentScope: MutableMap<String, NamedNode>
        ) {

            when (node) {

                is Program -> {
                    val newScope = makeScope()

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
                    currentStaticDepth++
                    maxStaticDepth = max(maxStaticDepth, currentStaticDepth)

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

                    currentStaticDepth--
                }

                is Function.Parameter -> {
                    node.defaultValue?.let { analyzeNode(it, currentScope) }
                }

                // Expressions

                is Expression.Variable -> {
                    if (!checkVariableUsage(node.name, node))
                        nameDefinitions[Ref(node)] = Ref(visibleNames[node.name]!!.topVariable())
                }

                is Expression.FunctionCall -> {
                    if (!checkFunctionUsage(node)) {
                        nameDefinitions[Ref(node)] = Ref(visibleNames[node.name]!!.topFunction())
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

                is Expression.ArrayElement -> {
                    analyzeNode(node.expression, currentScope)
                    analyzeNode(node.index, currentScope)
                }

                is Expression.ArrayLength -> {
                    analyzeNode(node.expression, currentScope)
                }

                is Expression.ArrayAllocation -> {
                    analyzeNode(node.initialization, currentScope)
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
                    when (node.lvalue) {
                        is Statement.Assignment.LValue.Variable -> {
                            if (!checkVariableUsage(node.lvalue.name, node)) {
                                nameDefinitions[Ref(node)] = Ref(visibleNames[node.lvalue.name]!!.topVariable())
                                analyzeNode(node.value, currentScope)
                            }
                        }
                        is Statement.Assignment.LValue.ArrayElement -> {
                            // There's no way to resolve name for an arbitrary expression
                            analyzeNode(node.value, currentScope)
                        }
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

                is Statement.ForeachLoop -> {
                    val newScope = makeScope() // foreach body introduces a new scope

                    // receiving variable and generator call are treated as they belonged to the new scope
                    analyzeNode(node.receivingVariable, newScope)

                    // resolve name for generator call.
                    if (!checkGeneratorUsage(node.generatorCall)) {
                        nameDefinitions[Ref(node.generatorCall)] =
                            Ref(visibleNames[node.generatorCall.name]!!.topGenerator())
                    }
                    // skip analysing generatorCall because it would resolve the name again, but treating it as a regular function call
                    // instead move on to analysing its arguments directly
                    node.generatorCall.arguments.forEach { analyzeNode(it, currentScope) }

                    node.action.forEach { analyzeNode(it, newScope) }
                    destroyScope(newScope)
                }

                is Statement.GeneratorYield -> {
                    analyzeNode(node.value, currentScope)
                }

                else -> {}
            }
        }

        analyzeNode(ast, makeScope())

        if (failed)
            throw ResolutionFailed()

        return Result(nameDefinitions, maxStaticDepth)
    }
}
