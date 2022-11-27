package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.intermediate_form.addTreeToCFG
import compiler.common.intermediate_form.mergeCFGsUnconditionally
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceHashSet
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.ReferenceSet
import compiler.semantic_analysis.VariablePropertiesAnalyzer
import org.junit.Ignore
import kotlin.test.Test

class ExpressionControlFlowTest {

    private class TestFunctionDetailsGenerator(val function: Function) : FunctionDetailsGenerator(0, emptyMap(), emptyList()) {
        override fun generateCall(args: List<IntermediateFormTreeNode>): FunctionCallIntermediateForm {
            val callResult = IntermediateFormTreeNode.DummyCallResult()
            return FunctionCallIntermediateForm(
                addTreeToCFG(null, IntermediateFormTreeNode.DummyCall(function, args, callResult)),
                callResult
            )
        }

        override fun genRead(variable: Variable, isDirect: Boolean): IntermediateFormTreeNode {
            return IntermediateFormTreeNode.DummyRead(variable, isDirect)
        }

        override fun genWrite(variable: Variable, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode {
            return IntermediateFormTreeNode.DummyWrite(variable, value, isDirect)
        }
    }

    private class ExpressionContext(
        varNames: Set<String>,
        functions: Map<String, List<Type>> = emptyMap(), // first element is return type
        funToAffectedVar: Map<String, Set<String>> = emptyMap(),
        val currentFunction: Function = Function("dummy", emptyList(), Type.Unit, emptyList()),
        val callGraph: ReferenceHashMap<String, ReferenceSet<String>> = ReferenceHashMap(),
        val argumentResolution: ReferenceMap<Expression.FunctionCall.Argument, Function.Parameter> = ReferenceHashMap()
    ) {
        val nameResolution: ReferenceHashMap<Any, NamedNode> = ReferenceHashMap()
        val nameToVarMap = HashMap<String, Variable>()
        val nameToFunMap = HashMap<String, Function>()
        val functionDetailsGenerators = ReferenceHashMap<Function, FunctionDetailsGenerator>()
        val variableProperties = ReferenceHashMap<Any, VariablePropertiesAnalyzer.VariableProperties>()
        val finalCallGraph: ReferenceHashMap<Function, ReferenceSet<Function>> = ReferenceHashMap()
        init {
            for (name in varNames) {
                nameToVarMap[name] = Variable(Variable.Kind.VARIABLE, name, Type.Number, null)
                variableProperties[nameToVarMap[name]!!] = VariablePropertiesAnalyzer.VariableProperties(currentFunction, mutableSetOf(), mutableSetOf())
            }
            for (name in functions.keys) {
                nameToFunMap[name] = Function(
                    name,
                    functions[name]!!.subList(1, functions[name]!!.size).map { Function.Parameter("", it, null) },
                    functions[name]!![0],
                    emptyList()
                )

                val referenceHashSet = ReferenceHashSet<Function>()
                referenceHashSet.add(nameToFunMap[name]!!)
                finalCallGraph[nameToFunMap[name]!!] = referenceHashSet
            }
            for (function in nameToFunMap.values union setOf(currentFunction)) {
                functionDetailsGenerators[function] = TestFunctionDetailsGenerator(function)
            }
            for (entry in funToAffectedVar) {
                for (variable in entry.value) {
                    variableProperties[nameToVarMap[variable]]!!.writtenIn.add(nameToFunMap[entry.key]!!)
                }
            }
        }

        fun createCfg(expr: Expression, targetVariable: Variable? = null): ControlFlowGraph {
            return ControlFlow.createGraphForExpression(expr, targetVariable, currentFunction, nameResolution, variableProperties, finalCallGraph, functionDetailsGenerators, argumentResolution)
        }
    }

    private infix fun String.asVarIn(exprContext: ExpressionContext): Variable {
        return exprContext.nameToVarMap[this]!!
    }
    private infix fun String.asFunIn(exprContext: ExpressionContext): Function {
        return exprContext.nameToFunMap[this]!!
    }
    private infix fun Pair<String, List<Expression>>.asFunCallIn(exprContext: ExpressionContext): Expression.FunctionCall {
        val result = Expression.FunctionCall(this.first, this.second.map { Expression.FunctionCall.Argument(null, it) })
        exprContext.nameResolution[result] = exprContext.nameToFunMap[this.first]!!
        return result
    }
    private infix fun String.asFunCallIn(exprContext: ExpressionContext): Expression.FunctionCall {
        return Pair<String, List<Expression>>(this, emptyList()) asFunCallIn exprContext
    }
    private infix fun String.withArgs(list: List<Expression>): Pair<String, List<Expression>> {
        return Pair(this, list)
    }
    private infix fun String.asVarExprIn(exprContext: ExpressionContext): Expression.Variable {
        val result = Expression.Variable(this)
        exprContext.nameResolution[result] = this asVarIn exprContext
        return result
    }

    private infix fun Expression.add(expr: Expression): Expression {
        return Expression.BinaryOperation(Expression.BinaryOperation.Kind.ADD, this, expr)
    }

    private infix fun ControlFlowGraph.hasSameStructureAs(cfg: ControlFlowGraph): Boolean {
        val registersMap = ReferenceHashMap<Register, Register>()
        val callResultsMap = ReferenceHashMap<IntermediateFormTreeNode.DummyCallResult, IntermediateFormTreeNode.DummyCallResult>()
        val nodeMap = ReferenceHashMap<IntermediateFormTreeNode, IntermediateFormTreeNode>()

        fun <T> ReferenceHashMap<T, T>.isBijection(a: T, b: T): Boolean {
            if (!this.containsKey(a)) {
                this[a] = b
                return true
            }
            return this[a]!! === b
        }

        infix fun IntermediateFormTreeNode.hasSameStructureAs(iftNode: IntermediateFormTreeNode): Boolean {
            if (this::class != iftNode::class) return false
            if (!(nodeMap.isBijection(this, iftNode))) return false
            return when (this) {
                is IntermediateFormTreeNode.BinaryOperator -> {
                    (this.left hasSameStructureAs (iftNode as IntermediateFormTreeNode.BinaryOperator).left) &&
                        (this.right hasSameStructureAs iftNode.right)
                }
                is IntermediateFormTreeNode.UnaryOperator -> {
                    this.node hasSameStructureAs (iftNode as IntermediateFormTreeNode.UnaryOperator).node
                }

                is IntermediateFormTreeNode.DummyCall -> {
                    if (this.function != (iftNode as IntermediateFormTreeNode.DummyCall).function) return false
                    if (this.args.size != iftNode.args.size) return false
                    for (i in 0 until this.args.size) {
                        if (!nodeMap.isBijection(this.args[i], iftNode.args[i])) return false
                    }
                    return this.callResult hasSameStructureAs iftNode.callResult
                }
                is IntermediateFormTreeNode.DummyCallResult -> callResultsMap.isBijection(this, iftNode as IntermediateFormTreeNode.DummyCallResult)
                is IntermediateFormTreeNode.DummyWrite -> (this.variable == (iftNode as IntermediateFormTreeNode.DummyWrite).variable) && (this.isDirect == iftNode.isDirect) && (nodeMap.isBijection(this.value, iftNode.value))
                is IntermediateFormTreeNode.MemoryWrite -> (this.address == (iftNode as IntermediateFormTreeNode.MemoryWrite).address) && (this.node hasSameStructureAs iftNode.node)
                is IntermediateFormTreeNode.RegisterWrite -> registersMap.isBijection(this.register, (iftNode as IntermediateFormTreeNode.RegisterWrite).register) && (this.node hasSameStructureAs iftNode.node)
                is IntermediateFormTreeNode.RegisterRead -> registersMap.isBijection(this.register, (iftNode as IntermediateFormTreeNode.RegisterRead).register)
                else -> { this == iftNode }
            }
        }

        if (this.treeRoots.size != cfg.treeRoots.size) return false
        for (i in 0 until this.treeRoots.size) {
            if (!(this.treeRoots[i] hasSameStructureAs cfg.treeRoots[i])) return false
        }

        fun checkLinks(left: ReferenceMap<IntermediateFormTreeNode, IntermediateFormTreeNode>, right: ReferenceMap<IntermediateFormTreeNode, IntermediateFormTreeNode>): Boolean {
            if (left.size != right.size) return false
            for ((begin, end) in left) {
                if (!(right[nodeMap[begin]] === nodeMap[end])) return false
            }
            return true
        }

        return checkLinks(this.unconditionalLinks, cfg.unconditionalLinks) &&
            checkLinks(this.conditionalTrueLinks, cfg.conditionalTrueLinks) &&
            checkLinks(this.conditionalFalseLinks, cfg.conditionalFalseLinks)
    }

    private fun IntermediateFormTreeNode.toCfg(): ControlFlowGraph =
        addTreeToCFG(null, this)

    private infix fun ControlFlowGraph.merge(iftNode: IntermediateFormTreeNode): ControlFlowGraph =
        mergeCFGsUnconditionally(this, iftNode.toCfg())!!
    private infix fun IntermediateFormTreeNode.merge(iftNode: IntermediateFormTreeNode): ControlFlowGraph =
        mergeCFGsUnconditionally(this.toCfg(), iftNode.toCfg())!!

    @Ignore
    @Test
    fun `function calls test`() {
        val context = ExpressionContext(
            setOf("x"),
            mapOf(
                "f" to listOf(Type.Number),
                "g" to listOf(Type.Number)
            ),
            mapOf(
                "f" to setOf("x")
            )
        )

        val r1 = Register()
        val r2 = Register()
        val callResult = IntermediateFormTreeNode.DummyCallResult()

        val basicCall = context.createCfg("f" asFunCallIn context)
        val callAffectingVariable = context.createCfg(("x" asVarExprIn context) add ("f" asFunCallIn context))
        val callNotAffectingVariable = context.createCfg(("x" asVarExprIn context) add ("g" asFunCallIn context))
        val variableAfterAffectingFunction = context.createCfg(("f" asFunCallIn context) add ("x" asVarExprIn context))
        val variableAfterNotAffectingFunction = context.createCfg(("g" asFunCallIn context) add ("x" asVarExprIn context))

        assert(
            basicCall hasSameStructureAs (
                IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r1, callResult)
                    merge IntermediateFormTreeNode.RegisterRead(r1)
                )
        )
        assert(
            callAffectingVariable hasSameStructureAs (
                IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                    merge IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r2, callResult)
                    merge IntermediateFormTreeNode.Add(IntermediateFormTreeNode.RegisterRead(r1), IntermediateFormTreeNode.RegisterRead(r2))
                )
        )

        assert(
            callNotAffectingVariable hasSameStructureAs (
                IntermediateFormTreeNode.DummyCall("g" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r1, callResult)
                    merge IntermediateFormTreeNode.Add(IntermediateFormTreeNode.DummyRead("x" asVarIn context, true), IntermediateFormTreeNode.RegisterRead(r1))
                )
        )

        assert(
            variableAfterAffectingFunction hasSameStructureAs (
                IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r1, callResult)
                    merge IntermediateFormTreeNode.Add(IntermediateFormTreeNode.RegisterRead(r1), IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                )
        )
        assert(
            variableAfterNotAffectingFunction hasSameStructureAs (
                IntermediateFormTreeNode.DummyCall("g" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r1, callResult)
                    merge IntermediateFormTreeNode.Add(IntermediateFormTreeNode.RegisterRead(r1), IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                )
        )
    }
}
