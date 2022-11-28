package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.intermediate_form.FunctionDetailsGeneratorInterface
import compiler.common.intermediate_form.addTreeToCFG
import compiler.common.intermediate_form.mergeCFGsConditionally
import compiler.common.intermediate_form.mergeCFGsUnconditionally
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceSet
import compiler.common.reference_collections.referenceMapOf
import compiler.common.reference_collections.referenceSetOf
import compiler.semantic_analysis.VariablePropertiesAnalyzer
import org.junit.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class ExpressionControlFlowTest {

    private class TestFunctionDetailsGenerator(val function: Function) : FunctionDetailsGeneratorInterface {
        override fun generateCall(args: List<IntermediateFormTreeNode>): FunctionDetailsGeneratorInterface.FunctionCallIntermediateForm {
            val callResult = IntermediateFormTreeNode.DummyCallResult()
            return FunctionDetailsGeneratorInterface.FunctionCallIntermediateForm(
                addTreeToCFG(null, IntermediateFormTreeNode.DummyCall(function, args, callResult)),
                callResult
            )
        }

        override fun genPrologue(): ControlFlowGraph {
            throw NotImplementedError()
        }

        override fun genEpilogue(): ControlFlowGraph {
            throw NotImplementedError()
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
        functions: Map<String, Pair<Type, List<Type>>> = emptyMap(), // first element is return type
        funToAffectedVar: Map<String, Set<String>> = emptyMap(),
        val currentFunction: Function = Function("dummy", emptyList(), Type.Unit, emptyList()),
        val callGraph: ReferenceHashMap<String, ReferenceSet<String>> = ReferenceHashMap()
    ) {
        val nameResolution: ReferenceHashMap<Any, NamedNode> = ReferenceHashMap()
        var nameToVarMap: Map<String, Variable>
        var nameToFunMap: Map<String, Function>
        val functionDetailsGenerators = ReferenceHashMap<Function, FunctionDetailsGeneratorInterface>()
        val variableProperties = ReferenceHashMap<Any, VariablePropertiesAnalyzer.VariableProperties>()
        val finalCallGraph: ReferenceHashMap<Function, ReferenceSet<Function>> = ReferenceHashMap()
        val argumentResolution: ReferenceHashMap<Expression.FunctionCall.Argument, Function.Parameter> = ReferenceHashMap()

        init {
            nameToVarMap = varNames.associateWith { Variable(Variable.Kind.VARIABLE, it, Type.Number, null) }
            for (name in varNames) {
                variableProperties[nameToVarMap[name]!!] = VariablePropertiesAnalyzer.VariableProperties(currentFunction, mutableSetOf(), mutableSetOf())
            }
            nameToFunMap = functions.keys.associateWith {
                Function(
                    it,
                    functions[it]!!.second.map { Function.Parameter("", it, null) },
                    functions[it]!!.first,
                    emptyList()
                )
            }
            for (name in functions.keys) {
                finalCallGraph[nameToFunMap[name]!!] = referenceSetOf(nameToFunMap[name]!!)
            }
            for (function in nameToFunMap.values union setOf(currentFunction)) {
                functionDetailsGenerators[function] = TestFunctionDetailsGenerator(function)
            }
            funToAffectedVar.forEach {
                for (variable in it.value) {
                    variableProperties[nameToVarMap[variable]]!!.writtenIn.add(nameToFunMap[it.key]!!)
                }
            }
        }

        fun createCfg(expr: Expression, targetVariable: Variable? = null): ControlFlowGraph {
            return ControlFlow.createGraphForExpression(
                expr,
                targetVariable,
                currentFunction,
                nameResolution,
                variableProperties,
                finalCallGraph,
                functionDetailsGenerators,
                argumentResolution,
                referenceMapOf()
            )
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
        for (i in 0 until result.arguments.size) {
            exprContext.argumentResolution[result.arguments[i]] = (this.first asFunIn exprContext).parameters[i]
        }
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

    private infix fun Expression.and(expr: Expression): Expression {
        return Expression.BinaryOperation(Expression.BinaryOperation.Kind.AND, this, expr)
    }

    private infix fun Expression.or(expr: Expression): Expression {
        return Expression.BinaryOperation(Expression.BinaryOperation.Kind.OR, this, expr)
    }

    private fun ternary(cond: Expression, ifTrue: Expression, ifFalse: Expression): Expression {
        return Expression.Conditional(cond, ifTrue, ifFalse)
    }

    private infix fun ControlFlowGraph.hasSameStructureAs(cfg: ControlFlowGraph): Boolean {
        val registersMap = ReferenceHashMap<Register, Register>()
        val callResultsMap = ReferenceHashMap<IntermediateFormTreeNode.DummyCallResult, IntermediateFormTreeNode.DummyCallResult>()
        val nodeMap = ReferenceHashMap<IntermediateFormTreeNode, IntermediateFormTreeNode>()

        fun <T> ReferenceHashMap<T, T>.ensurePairSymmetrical(a: T, b: T): Boolean {
            if (!this.containsKey(a)) {
                this[a] = b
                return true
            }
            return this[a]!! === b
        }

        infix fun IntermediateFormTreeNode.hasSameStructureAs(iftNode: IntermediateFormTreeNode): Boolean {
            if (this::class != iftNode::class) return false
            if (!(nodeMap.ensurePairSymmetrical(this, iftNode))) return false
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
                    (this.args zip iftNode.args).forEach {
                        if (!nodeMap.ensurePairSymmetrical(it.first, it.second)) return false
                    }
                    return this.callResult hasSameStructureAs iftNode.callResult
                }

                is IntermediateFormTreeNode.DummyCallResult -> callResultsMap.ensurePairSymmetrical(this, iftNode as IntermediateFormTreeNode.DummyCallResult)
                is IntermediateFormTreeNode.DummyWrite -> (this.variable == (iftNode as IntermediateFormTreeNode.DummyWrite).variable) && (this.isDirect == iftNode.isDirect) && (nodeMap.ensurePairSymmetrical(this.value, iftNode.value))
                is IntermediateFormTreeNode.MemoryWrite -> (this.address == (iftNode as IntermediateFormTreeNode.MemoryWrite).address) && (this.node hasSameStructureAs iftNode.node)
                is IntermediateFormTreeNode.RegisterWrite -> registersMap.ensurePairSymmetrical(this.register, (iftNode as IntermediateFormTreeNode.RegisterWrite).register) && (this.node hasSameStructureAs iftNode.node)
                is IntermediateFormTreeNode.RegisterRead -> registersMap.ensurePairSymmetrical(this.register, (iftNode as IntermediateFormTreeNode.RegisterRead).register)
                else -> {
                    this == iftNode
                }
            }
        }

        if (this.treeRoots.size != cfg.treeRoots.size) return false

        fun dfs(left: IntermediateFormTreeNode, right: IntermediateFormTreeNode): Boolean {
            if (! (left hasSameStructureAs right)) return false

            if (this.unconditionalLinks.containsKey(left)) {
                if (!cfg.unconditionalLinks.containsKey(right)) return false
                val leftNext = this.unconditionalLinks[left]!!
                val rightNext = cfg.unconditionalLinks[right]!!
                if (nodeMap.containsKey(leftNext)) {
                    if (!(nodeMap[leftNext]!! === rightNext)) return false
                } else {
                    if (!dfs(leftNext, rightNext)) return false
                }
            }

            if (this.conditionalTrueLinks.containsKey(left)) {
                if (!cfg.conditionalTrueLinks.containsKey(right)) return false
                val leftNext = this.conditionalTrueLinks[left]!!
                val rightNext = cfg.conditionalTrueLinks[right]!!
                if (nodeMap.containsKey(leftNext)) {
                    if (!(nodeMap[leftNext]!! === rightNext)) return false
                } else {
                    if (!dfs(leftNext, rightNext)) return false
                }
            }

            if (this.conditionalFalseLinks.containsKey(left)) {
                if (!cfg.conditionalFalseLinks.containsKey(right)) return false
                val leftNext = this.conditionalFalseLinks[left]!!
                val rightNext = cfg.conditionalFalseLinks[right]!!
                if (nodeMap.containsKey(leftNext)) {
                    if (!(nodeMap[leftNext]!! === rightNext)) return false
                } else {
                    if (!dfs(leftNext, rightNext)) return false
                }
            }

            return true
        }

        return if (this.entryTreeRoot == null)
            cfg.entryTreeRoot == null
        else
            dfs(this.entryTreeRoot!!, cfg.entryTreeRoot!!)
    }

    private fun IntermediateFormTreeNode.toCfg(): ControlFlowGraph =
        addTreeToCFG(null, this)

    private infix fun IntermediateFormTreeNode.merge(cfg: ControlFlowGraph): ControlFlowGraph =
        mergeCFGsUnconditionally(this.toCfg(), cfg)!!

    private infix fun ControlFlowGraph.merge(cfg: ControlFlowGraph): ControlFlowGraph =
        mergeCFGsUnconditionally(this, cfg)!!

    private infix fun ControlFlowGraph.merge(iftNode: IntermediateFormTreeNode): ControlFlowGraph =
        mergeCFGsUnconditionally(this, iftNode.toCfg())!!

    private infix fun IntermediateFormTreeNode.merge(iftNode: IntermediateFormTreeNode): ControlFlowGraph =
        mergeCFGsUnconditionally(this.toCfg(), iftNode.toCfg())!!

    @Ignore
    @Test
    fun `basic expressions`() {
        val context = ExpressionContext(
            setOf("x", "y")
        )

        val xExpr = "x" asVarExprIn context
        val yExpr = "y" asVarExprIn context
        val xVarRead = IntermediateFormTreeNode.DummyRead("x" asVarIn context, true)
        val yVarRead = IntermediateFormTreeNode.DummyRead("y" asVarIn context, true)

        val basic = context.createCfg(xExpr)
        val operatorTests = mapOf(

            Expression.BooleanLiteral(true) to IntermediateFormTreeNode.Const(1),
            Expression.NumberLiteral(5) to IntermediateFormTreeNode.Const(5),

            Expression.UnaryOperation(Expression.UnaryOperation.Kind.NOT, xExpr) to IntermediateFormTreeNode.LogicalNegation(xVarRead),
            Expression.UnaryOperation(Expression.UnaryOperation.Kind.BIT_NOT, xExpr) to IntermediateFormTreeNode.BitNegation(xVarRead),
            Expression.UnaryOperation(Expression.UnaryOperation.Kind.MINUS, xExpr) to IntermediateFormTreeNode.Negation(xVarRead),
            Expression.UnaryOperation(Expression.UnaryOperation.Kind.PLUS, xExpr) to xVarRead,

            Expression.BinaryOperation(Expression.BinaryOperation.Kind.ADD, xExpr, yExpr) to IntermediateFormTreeNode.Add(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.MULTIPLY, xExpr, yExpr) to IntermediateFormTreeNode.Multiply(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.DIVIDE, xExpr, yExpr) to IntermediateFormTreeNode.Divide(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.SUBTRACT, xExpr, yExpr) to IntermediateFormTreeNode.Subtract(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.MODULO, xExpr, yExpr) to IntermediateFormTreeNode.Modulo(xVarRead, yVarRead),

            Expression.BinaryOperation(Expression.BinaryOperation.Kind.BIT_AND, xExpr, yExpr) to IntermediateFormTreeNode.BitAnd(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.BIT_OR, xExpr, yExpr) to IntermediateFormTreeNode.BitOr(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.BIT_XOR, xExpr, yExpr) to IntermediateFormTreeNode.BitXor(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.BIT_SHIFT_LEFT, xExpr, yExpr) to IntermediateFormTreeNode.BitShiftLeft(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.BIT_SHIFT_RIGHT, xExpr, yExpr) to IntermediateFormTreeNode.BitShiftRight(xVarRead, yVarRead),

            Expression.BinaryOperation(Expression.BinaryOperation.Kind.XOR, xExpr, yExpr) to IntermediateFormTreeNode.LogicalXor(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.IFF, xExpr, yExpr) to IntermediateFormTreeNode.LogicalIff(xVarRead, yVarRead),

            Expression.BinaryOperation(Expression.BinaryOperation.Kind.EQUALS, xExpr, yExpr) to IntermediateFormTreeNode.Equals(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.NOT_EQUALS, xExpr, yExpr) to IntermediateFormTreeNode.NotEquals(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.LESS_THAN, xExpr, yExpr) to IntermediateFormTreeNode.LessThan(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS, xExpr, yExpr) to IntermediateFormTreeNode.LessThanOrEquals(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.GREATER_THAN, xExpr, yExpr) to IntermediateFormTreeNode.GreaterThan(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.GREATER_THAN_OR_EQUALS, xExpr, yExpr) to IntermediateFormTreeNode.GreaterThanOrEquals(xVarRead, yVarRead),
        )

        assertTrue(basic hasSameStructureAs IntermediateFormTreeNode.DummyRead("x" asVarIn context, true).toCfg())

        for ((expr, iftNode) in operatorTests) {
            assertTrue(context.createCfg(expr) hasSameStructureAs iftNode.toCfg(), expr.toString())
        }
    }

    @Ignore
    @Test
    fun `assignment`() {
        val context = ExpressionContext(
            setOf("x", "y")
        )

        val assignmentCfg = context.createCfg("x" asVarExprIn context, "y" asVarIn context) // y = x

        assertTrue(
            assignmentCfg hasSameStructureAs IntermediateFormTreeNode.DummyWrite("y" asVarIn context, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true), true).toCfg()
        )
    }

    @Ignore
    @Test
    fun `function calls test`() {
        val context = ExpressionContext(
            setOf("x"),
            mapOf(
                "f" to Pair(Type.Number, emptyList()),
                "g" to Pair(Type.Number, emptyList())
            ),
            mapOf(
                "f" to setOf("x")
            )
        )

        val r1 = Register()
        val r2 = Register()
        val callResult = IntermediateFormTreeNode.DummyCallResult()

        val basicCall = context.createCfg("f" asFunCallIn context) // f()
        assertTrue(
            basicCall hasSameStructureAs (
                IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r1, callResult)
                    merge IntermediateFormTreeNode.RegisterRead(r1)
                )
        )

        val callAffectingVariable = context.createCfg( // x + f(), f affects x
            ("x" asVarExprIn context)
                add ("f" asFunCallIn context)
        )
        assertTrue(
            callAffectingVariable hasSameStructureAs (
                IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                    merge IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r2, callResult)
                    merge IntermediateFormTreeNode.Add(IntermediateFormTreeNode.RegisterRead(r1), IntermediateFormTreeNode.RegisterRead(r2))
                )
        )

        val callNotAffectingVariable = context.createCfg( // x + g(), g does not affect x
            ("x" asVarExprIn context)
                add ("g" asFunCallIn context)
        )
        assertTrue(
            callNotAffectingVariable hasSameStructureAs (
                IntermediateFormTreeNode.DummyCall("g" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r1, callResult)
                    merge IntermediateFormTreeNode.Add(IntermediateFormTreeNode.DummyRead("x" asVarIn context, true), IntermediateFormTreeNode.RegisterRead(r1))
                )
        )

        val variableAfterAffectingFunction = context.createCfg( // f() + x, f affects x
            ("f" asFunCallIn context)
                add ("x" asVarExprIn context)
        )
        assertTrue(
            variableAfterAffectingFunction hasSameStructureAs (
                IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r1, callResult)
                    merge IntermediateFormTreeNode.Add(IntermediateFormTreeNode.RegisterRead(r1), IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                )
        )

        val variableAfterNotAffectingFunction = context.createCfg( // g() + x, g does not affect x
            ("g" asFunCallIn context)
                add ("x" asVarExprIn context)
        )
        assertTrue(
            variableAfterNotAffectingFunction hasSameStructureAs (
                IntermediateFormTreeNode.DummyCall("g" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r1, callResult)
                    merge IntermediateFormTreeNode.Add(IntermediateFormTreeNode.RegisterRead(r1), IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                )
        )

        val variableOnBothSidesOfFunction = context.createCfg( // x + f() + x, f -> x
            ("x" asVarExprIn context)
                add ("f" asFunCallIn context)
                add ("x" asVarExprIn context)
        )
        assertTrue(
            variableOnBothSidesOfFunction hasSameStructureAs (
                IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                    merge IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r2, callResult)
                    merge IntermediateFormTreeNode.Add(IntermediateFormTreeNode.Add(IntermediateFormTreeNode.RegisterRead(r1), IntermediateFormTreeNode.RegisterRead(r2)), IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                )
        )

        val multipleUsageBeforeCall = context.createCfg( // x + x + f(), f -> x
            ("x" asVarExprIn context)
                add ("x" asVarExprIn context)
                add ("f" asFunCallIn context)
        )
        assertTrue(
            multipleUsageBeforeCall hasSameStructureAs (
                IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                    merge IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IntermediateFormTreeNode.RegisterWrite(r2, callResult)
                    merge IntermediateFormTreeNode.Add(
                        IntermediateFormTreeNode.Add(
                            IntermediateFormTreeNode.RegisterRead(r1),
                            IntermediateFormTreeNode.RegisterRead(r1)
                        ),
                        IntermediateFormTreeNode.RegisterRead(r2)
                    )
                )
        )
    }

    @Ignore
    @Test
    fun `conditionals`() {
        val context = ExpressionContext(
            setOf("x", "y", "z")
        )

        val r1 = Register()

        val andCfg = context.createCfg( // x oraz y
            ("x" asVarExprIn context)
                and ("y" asVarExprIn context)
        )
        assertTrue(
            andCfg hasSameStructureAs (
                mergeCFGsConditionally(
                    IntermediateFormTreeNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("y" asVarIn context, true)).toCfg(),
                    IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.Const(0)).toCfg()
                )
                    merge IntermediateFormTreeNode.RegisterRead(r1)
                )
        )

        val orCfg = context.createCfg( // x lub y
            ("x" asVarExprIn context)
                or ("y" asVarExprIn context)
        )
        assertTrue(
            orCfg hasSameStructureAs (
                mergeCFGsConditionally(
                    IntermediateFormTreeNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.Const(1)).toCfg(),
                    IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("y" asVarIn context, true)).toCfg()
                )
                    merge IntermediateFormTreeNode.RegisterRead(r1)
                )
        )

        val ternaryCfg = context.createCfg( // x ? y : z
            ternary(
                "x" asVarExprIn context,
                "y" asVarExprIn context,
                "z" asVarExprIn context
            )
        )
        assertTrue(
            ternaryCfg hasSameStructureAs (
                mergeCFGsConditionally(
                    IntermediateFormTreeNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("y" asVarIn context, true)).toCfg(),
                    IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("z" asVarIn context, true)).toCfg()
                )
                    merge IntermediateFormTreeNode.RegisterRead(r1)
                )
        )
    }

    @Ignore
    @Test
    fun `conditionals with function calls`() {
        val context = ExpressionContext(
            setOf("x", "y", "z"),
            mapOf(
                "f" to Pair(Type.Number, emptyList()),
                "g" to Pair(Type.Number, emptyList()),
                "h" to Pair(Type.Number, emptyList()),
            ),
            mapOf(
                "f" to setOf("x"),
                "g" to setOf("y"),
                "h" to setOf("z"),
            )
        )

        val r1 = Register()
        val r2 = Register()
        val r3 = Register()
        val r4 = Register()
        val r5 = Register()
        val r6 = Register()
        val r7 = Register()
        val callResult1 = IntermediateFormTreeNode.DummyCallResult()
        val callResult2 = IntermediateFormTreeNode.DummyCallResult()
        val callResult3 = IntermediateFormTreeNode.DummyCallResult()

        val variableInConditional = context.createCfg( // ( x ? x : x ) + f(), f -> x
            ternary(
                "x" asVarExprIn context,
                "x" asVarExprIn context,
                "x" asVarExprIn context
            ) add ("f" asFunCallIn context)
        )
        assertTrue(
            variableInConditional hasSameStructureAs (
                mergeCFGsConditionally(
                    IntermediateFormTreeNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true)).toCfg(),
                    IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true)).toCfg()
                )
                    merge IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                    merge IntermediateFormTreeNode.RegisterWrite(r2, callResult1)
                    merge IntermediateFormTreeNode.Add(IntermediateFormTreeNode.RegisterRead(r1), IntermediateFormTreeNode.RegisterRead(r2))
                )
        )

        val functionCallsInConditional = context.createCfg( // x + y + z + ( f() ? g() : h() ), f -> x, g -> y, h -> z
            ("x" asVarExprIn context) add ("y" asVarExprIn context) add ("z" asVarExprIn context) add
                ternary(
                    "f" asFunCallIn context,
                    "g" asFunCallIn context,
                    "h" asFunCallIn context,
                )
        )
        assertTrue(
            functionCallsInConditional hasSameStructureAs (
                IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                    merge IntermediateFormTreeNode.RegisterWrite(r2, IntermediateFormTreeNode.DummyRead("y" asVarIn context, true))
                    merge IntermediateFormTreeNode.RegisterWrite(r3, IntermediateFormTreeNode.DummyRead("z" asVarIn context, true))
                    merge mergeCFGsConditionally(
                        IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                            merge IntermediateFormTreeNode.RegisterWrite(r4, callResult1)
                            merge IntermediateFormTreeNode.RegisterRead(r4),
                        IntermediateFormTreeNode.DummyCall("g" asFunIn context, emptyList(), callResult2)
                            merge IntermediateFormTreeNode.RegisterWrite(r5, callResult2)
                            merge IntermediateFormTreeNode.RegisterWrite(r7, IntermediateFormTreeNode.RegisterRead(r5)),
                        IntermediateFormTreeNode.DummyCall("h" asFunIn context, emptyList(), callResult3)
                            merge IntermediateFormTreeNode.RegisterWrite(r6, callResult3)
                            merge IntermediateFormTreeNode.RegisterWrite(r7, IntermediateFormTreeNode.RegisterRead(r6))
                    )
                    merge IntermediateFormTreeNode.Add(
                        IntermediateFormTreeNode.Add(
                            IntermediateFormTreeNode.Add(
                                IntermediateFormTreeNode.RegisterRead(r1),
                                IntermediateFormTreeNode.RegisterRead(r2)
                            ),
                            IntermediateFormTreeNode.RegisterRead(r3)
                        ),
                        IntermediateFormTreeNode.RegisterRead(r7)
                    )
                )
        )

        val andWithFunction = context.createCfg( // x oraz f(), f -> x
            ("x" asVarExprIn context)
                and ("f" asFunCallIn context)
        )
        assertTrue(
            andWithFunction hasSameStructureAs (
                mergeCFGsConditionally(
                    IntermediateFormTreeNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                        merge IntermediateFormTreeNode.RegisterWrite(r2, callResult1)
                        merge IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.RegisterRead(r2)),
                    IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.Const(0)).toCfg()
                )
                    merge IntermediateFormTreeNode.RegisterRead(r1)
                )
        )

        val variableAndFunctionInTernary = context.createCfg( // x ? x : f(), f -> x
            ternary(
                "x" asVarExprIn context,
                "x" asVarExprIn context,
                "f" asFunCallIn context
            )
        )
        assertTrue(
            variableAndFunctionInTernary hasSameStructureAs (
                mergeCFGsConditionally(
                    IntermediateFormTreeNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true)).toCfg(),
                    IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                        merge IntermediateFormTreeNode.RegisterWrite(r2, callResult1)
                        merge IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.RegisterRead(r2))
                )
                    merge IntermediateFormTreeNode.RegisterRead(r1)
                )
        )
    }

    @Ignore
    @Test
    fun `function calls with arguments`() {
        val context = ExpressionContext(
            setOf("x"),
            mapOf(
                "f" to Pair(Type.Number, emptyList()),
                "g" to Pair(Type.Number, listOf(Type.Number, Type.Number)),
                "h" to Pair(Type.Number, listOf(Type.Number)),
            ),
            mapOf(
                "f" to setOf("x")
            )
        )

        val r1 = Register()
        val r2 = Register()
        val r3 = Register()
        val callResult1 = IntermediateFormTreeNode.DummyCallResult()
        val callResult2 = IntermediateFormTreeNode.DummyCallResult()

        val read1 = IntermediateFormTreeNode.RegisterRead(r1)
        val read2 = IntermediateFormTreeNode.RegisterRead(r2)

        val multipleArguments = context.createCfg( // g( x, f() ), f -> x
            "g" withArgs listOf("x" asVarExprIn context, "f" asFunCallIn context)
                asFunCallIn context
        )
        assertTrue(
            multipleArguments hasSameStructureAs (
                IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                    merge IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                    merge IntermediateFormTreeNode.RegisterWrite(r2, callResult1)
                    merge read1
                    merge read2
                    merge IntermediateFormTreeNode.DummyCall("g" asFunIn context, listOf(read1, read2), callResult2)
                    merge IntermediateFormTreeNode.RegisterWrite(r3, callResult2)
                    merge IntermediateFormTreeNode.RegisterRead(r3)
                )
        )

        val nestedArguments = context.createCfg( // x + h(f()), f -> x
            ("x" asVarExprIn context)
                add ("h" withArgs listOf("f" asFunCallIn context) asFunCallIn context)
        )
        assertTrue(
            nestedArguments hasSameStructureAs (
                IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                    merge IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                    merge IntermediateFormTreeNode.RegisterWrite(r2, callResult1)
                    merge read2
                    merge IntermediateFormTreeNode.DummyCall("g" asFunIn context, listOf(read2), callResult2)
                    merge IntermediateFormTreeNode.RegisterWrite(r3, callResult2)
                    merge IntermediateFormTreeNode.Add(IntermediateFormTreeNode.RegisterRead(r1), IntermediateFormTreeNode.RegisterRead(r3))
                )
        )
    }

    @Ignore
    @Test
    fun `execution order test`() {
        val context = ExpressionContext(
            setOf("x", "y", "a", "b"),
            mapOf(
                "f" to Pair(Type.Number, emptyList()),
                "g" to Pair(Type.Number, listOf(Type.Number, Type.Number)),
                "h" to Pair(Type.Number, emptyList()),
            ),
            mapOf(
                "f" to setOf("x"),
                "h" to setOf("b"),
            )
        )

        val r1 = Register()
        val r2 = Register()
        val r3 = Register()
        val r4 = Register()
        val r5 = Register()
        val r6 = Register()
        val r7 = Register()
        val r8 = Register()
        val callResult1 = IntermediateFormTreeNode.DummyCallResult()
        val callResult2 = IntermediateFormTreeNode.DummyCallResult()
        val callResult3 = IntermediateFormTreeNode.DummyCallResult()

        val read3 = IntermediateFormTreeNode.RegisterRead(r3)
        val read4 = IntermediateFormTreeNode.RegisterRead(r4)

        val cfg = context.createCfg( //   x + ( g( a + f() , b ) + ( ( b + x ) + ( h() ? x : y ) ) ), f -> x, h -> b
            ("x" asVarExprIn context)
                add (
                    (
                        "g" withArgs listOf(
                            ("a" asVarExprIn context) add ("f" asFunCallIn context),
                            ("b" asVarExprIn context)
                        ) asFunCallIn context
                        )
                        add (
                            (("b" asVarExprIn context) add ("x" asVarExprIn context))
                                add ternary(
                                    "h" asFunCallIn context,
                                    "x" asVarExprIn context,
                                    "y" asVarExprIn context
                                )
                            )
                    )
        )

        assertTrue(
            cfg hasSameStructureAs (
                IntermediateFormTreeNode.RegisterWrite(r1, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true))
                    merge IntermediateFormTreeNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                    merge IntermediateFormTreeNode.RegisterWrite(r2, callResult1)
                    merge IntermediateFormTreeNode.RegisterWrite(
                        r3,
                        IntermediateFormTreeNode.Add(
                            IntermediateFormTreeNode.DummyRead("a" asVarIn context, true),
                            IntermediateFormTreeNode.RegisterRead(r2)
                        )
                    )
                    merge IntermediateFormTreeNode.RegisterWrite(r4, IntermediateFormTreeNode.DummyRead("b" asVarIn context, true))
                    merge read3 merge read4
                    merge IntermediateFormTreeNode.DummyCall("g" asFunIn context, listOf(read3, read4), callResult2)
                    merge IntermediateFormTreeNode.RegisterWrite(r5, callResult2)
                    merge IntermediateFormTreeNode.RegisterWrite(r6, IntermediateFormTreeNode.DummyRead("b" asVarIn context, true))
                    merge IntermediateFormTreeNode.DummyCall("h" asFunIn context, emptyList(), callResult3)
                    merge IntermediateFormTreeNode.RegisterWrite(r7, callResult3)
                    merge mergeCFGsConditionally(
                        IntermediateFormTreeNode.RegisterRead(r7).toCfg(),
                        IntermediateFormTreeNode.RegisterWrite(r8, IntermediateFormTreeNode.DummyRead("x" asVarIn context, true)).toCfg(),
                        IntermediateFormTreeNode.RegisterWrite(r8, IntermediateFormTreeNode.DummyRead("y" asVarIn context, true)).toCfg()
                    )
                    merge IntermediateFormTreeNode.Add(
                        IntermediateFormTreeNode.RegisterRead(r1),
                        IntermediateFormTreeNode.Add(
                            IntermediateFormTreeNode.RegisterRead(r5),
                            IntermediateFormTreeNode.Add(
                                IntermediateFormTreeNode.Add(
                                    IntermediateFormTreeNode.RegisterRead(r6),
                                    IntermediateFormTreeNode.DummyRead("x" asVarIn context, true)
                                ),
                                IntermediateFormTreeNode.RegisterRead(r8)
                            )
                        )
                    )
                )
        )
    }
}
