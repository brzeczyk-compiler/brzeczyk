package compiler.intermediate

import compiler.analysis.VariablePropertiesAnalyzer
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Type
import compiler.ast.Variable
import compiler.intermediate.generators.FunctionDetailsGenerator
import compiler.intermediate.generators.VariableAccessGenerator
import compiler.utils.Ref
import compiler.utils.keyRefMapOf
import compiler.utils.mutableKeyRefMapOf
import compiler.utils.mutableRefMapOf
import compiler.utils.refSetOf
import kotlin.test.Test
import kotlin.test.assertTrue

class ExpressionControlFlowTest {

    private class TestFunctionDetailsGenerator(val function: Function) :
        FunctionDetailsGenerator {
        override fun genCall(args: List<IFTNode>): FunctionDetailsGenerator.FunctionCallIntermediateForm {
            val callResult = IFTNode.DummyCallResult()
            return FunctionDetailsGenerator.FunctionCallIntermediateForm(
                ControlFlowGraphBuilder().addSingleTree(IFTNode.DummyCall(function, args, callResult)).build(),
                callResult,
                null
            )
        }

        override fun genPrologue(): ControlFlowGraph {
            throw NotImplementedError()
        }

        override fun genEpilogue(): ControlFlowGraph {
            throw NotImplementedError()
        }

        override val spilledRegistersRegionOffset get() = throw NotImplementedError()
        override val spilledRegistersRegionSize get() = throw NotImplementedError()
        override val identifier: String get() = throw NotImplementedError()

        override fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode {
            return IFTNode.DummyRead(namedNode, isDirect)
        }

        override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode {
            return IFTNode.DummyWrite(namedNode, value, isDirect)
        }
    }

    private class ExpressionContext(
        varNames: Set<String>,
        functions: Map<String, Pair<Type, List<Type>>> = emptyMap(), // first element is return type
        funToAffectedVar: Map<String, Set<String>> = emptyMap(),
        val currentFunction: Function = Function("dummy", emptyList(), Type.Unit, emptyList()),
        val globals: Set<String> = emptySet()
    ) {
        val nameResolution: MutableMap<Ref<AstNode>, Ref<NamedNode>> = mutableRefMapOf()
        var nameToVarMap: Map<String, Variable>
        var nameToParamMap: Map<String, Function.Parameter>
        var nameToFunMap: Map<String, Function>

        val functionDetailsGenerators = mutableKeyRefMapOf<Function, FunctionDetailsGenerator>()
        val variableProperties = mutableKeyRefMapOf<AstNode, VariablePropertiesAnalyzer.VariableProperties>()
        val finalCallGraph = mutableKeyRefMapOf<Function, Set<Ref<Function>>>()
        val argumentResolution: MutableMap<Ref<Expression.FunctionCall.Argument>, Ref<Function.Parameter>> = mutableRefMapOf()

        init {
            val mutableVariableProperties = mutableKeyRefMapOf<AstNode, VariablePropertiesAnalyzer.MutableVariableProperties>()

            nameToVarMap = varNames.associateWith { Variable(Variable.Kind.VARIABLE, it, Type.Number, null) }
            for (name in varNames) {
                mutableVariableProperties[Ref(nameToVarMap[name]!!)] =
                    VariablePropertiesAnalyzer.MutableVariableProperties(
                        if (name in globals) VariablePropertiesAnalyzer.GlobalContext else currentFunction
                    )
            }
            nameToParamMap = currentFunction.parameters.associateBy { it.name }
            for (param in currentFunction.parameters)
                mutableVariableProperties[Ref(param)] = VariablePropertiesAnalyzer.MutableVariableProperties(currentFunction)
            nameToFunMap = functions.keys.associateWith {
                Function(
                    it,
                    functions[it]!!.second.map { paramType -> Function.Parameter("", paramType, null) },
                    functions[it]!!.first,
                    emptyList()
                )
            }
            for (name in functions.keys) {
                finalCallGraph[Ref(nameToFunMap[name]!!)] = refSetOf(nameToFunMap[name]!!)
            }
            for (function in nameToFunMap.values union setOf(currentFunction)) {
                functionDetailsGenerators[Ref(function)] = TestFunctionDetailsGenerator(function)
            }
            funToAffectedVar.forEach {
                for (variable in it.value) {
                    mutableVariableProperties[Ref(nameToVarMap[variable] as AstNode)]!!.writtenIn.add(Ref(nameToFunMap[it.key]!!))
                }
            }

            for ((namedNode, mutableVP) in mutableVariableProperties.entries)
                variableProperties[namedNode] = VariablePropertiesAnalyzer.fixVariableProperties(mutableVP)
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
                keyRefMapOf(),
                object : VariableAccessGenerator {
                    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode =
                        IFTNode.DummyRead(namedNode, isDirect, true)

                    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode =
                        IFTNode.DummyWrite(namedNode, value, isDirect, true)
                }
            )
        }
    }

    private infix fun String.asVarIn(exprContext: ExpressionContext): Variable {
        return exprContext.nameToVarMap[this]!!
    }

    private infix fun String.asParamIn(exprContext: ExpressionContext): Function.Parameter {
        return exprContext.nameToParamMap[this]!!
    }

    private infix fun String.asFunIn(exprContext: ExpressionContext): Function {
        return exprContext.nameToFunMap[this]!!
    }

    private infix fun Pair<String, List<Expression>>.asFunCallIn(exprContext: ExpressionContext): Expression.FunctionCall {
        val result = Expression.FunctionCall(this.first, this.second.map { Expression.FunctionCall.Argument(null, it) })
        for (i in result.arguments.indices) {
            exprContext.argumentResolution[Ref(result.arguments[i])] = Ref((this.first asFunIn exprContext).parameters[i])
        }
        exprContext.nameResolution[Ref(result)] = Ref(exprContext.nameToFunMap[this.first]!!)
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
        exprContext.nameResolution[Ref(result)] = Ref(this asVarIn exprContext)
        return result
    }

    private infix fun String.asParamExprIn(exprContext: ExpressionContext): Expression.Variable {
        val result = Expression.Variable(this)
        exprContext.nameResolution[Ref(result)] = Ref(this asParamIn exprContext)
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
        val registersMap = mutableKeyRefMapOf<Register, Register>()
        val callResultsMap = mutableKeyRefMapOf<IFTNode.DummyCallResult, IFTNode.DummyCallResult>()
        val nodeMap = mutableKeyRefMapOf<IFTNode, IFTNode>()

        fun <T> MutableMap<Ref<T>, T>.ensurePairSymmetrical(a: T, b: T): Boolean {
            if (!this.containsKey(Ref(a))) {
                this[Ref(a)] = b
                return true
            }
            return this[Ref(a)]!! === b
        }

        infix fun IFTNode.hasSameStructureAs(iftNode: IFTNode): Boolean {

            if (this::class != iftNode::class) return false
            if (!(nodeMap.ensurePairSymmetrical(this, iftNode))) return false
            return when (this) {
                is IFTNode.BinaryOperator -> {
                    (this.left hasSameStructureAs (iftNode as IFTNode.BinaryOperator).left) &&
                        (this.right hasSameStructureAs iftNode.right)
                }

                is IFTNode.UnaryOperator -> {
                    this.node hasSameStructureAs (iftNode as IFTNode.UnaryOperator).node
                }

                is IFTNode.DummyCall -> {
                    if (this.function != (iftNode as IFTNode.DummyCall).function) return false
                    if (this.args.size != iftNode.args.size) return false
                    (this.args zip iftNode.args).forEach {
                        if (!nodeMap.ensurePairSymmetrical(it.first, it.second)) return false
                    }
                    return this.callResult hasSameStructureAs iftNode.callResult
                }

                is IFTNode.DummyCallResult -> callResultsMap.ensurePairSymmetrical(this, iftNode as IFTNode.DummyCallResult)
                is IFTNode.DummyWrite -> (this.namedNode == (iftNode as IFTNode.DummyWrite).namedNode) && (this.isDirect == iftNode.isDirect) && (this.isGlobal == iftNode.isGlobal) && (nodeMap.ensurePairSymmetrical(this.value, iftNode.value))
                is IFTNode.MemoryWrite -> (this.address == (iftNode as IFTNode.MemoryWrite).address) && (this.value hasSameStructureAs iftNode.value)
                is IFTNode.RegisterWrite -> registersMap.ensurePairSymmetrical(this.register, (iftNode as IFTNode.RegisterWrite).register) && (this.node hasSameStructureAs iftNode.node)
                is IFTNode.RegisterRead -> registersMap.ensurePairSymmetrical(this.register, (iftNode as IFTNode.RegisterRead).register)
                else -> {
                    this == iftNode
                }
            }
        }

        if (this.treeRoots.size != cfg.treeRoots.size) return false

        fun dfs(left: IFTNode, right: IFTNode): Boolean {
            if (! (left hasSameStructureAs right)) return false

            if (this.unconditionalLinks.containsKey(Ref(left))) {
                if (!cfg.unconditionalLinks.containsKey(Ref(right))) return false
                val leftNext = this.unconditionalLinks[Ref(left)]!!.value
                val rightNext = cfg.unconditionalLinks[Ref(right)]!!.value
                if (nodeMap.containsKey(Ref(leftNext))) {
                    if (nodeMap[Ref(leftNext)]!! !== rightNext) return false
                } else {
                    if (!dfs(leftNext, rightNext)) return false
                }
            }

            if (this.conditionalTrueLinks.containsKey(Ref(left))) {
                if (!cfg.conditionalTrueLinks.containsKey(Ref(right))) return false
                val leftNext = this.conditionalTrueLinks[Ref(left)]!!.value
                val rightNext = cfg.conditionalTrueLinks[Ref(right)]!!.value
                if (nodeMap.containsKey(Ref(leftNext))) {
                    if (nodeMap[Ref(leftNext)]!! !== rightNext) return false
                } else {
                    if (!dfs(leftNext, rightNext)) return false
                }
            }

            if (this.conditionalFalseLinks.containsKey(Ref(left))) {
                if (!cfg.conditionalFalseLinks.containsKey(Ref(right))) return false
                val leftNext = this.conditionalFalseLinks[Ref(left)]!!.value
                val rightNext = cfg.conditionalFalseLinks[Ref(right)]!!.value
                if (nodeMap.containsKey(Ref(leftNext))) {
                    if (nodeMap[Ref(leftNext)]!! !== rightNext) return false
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

    private fun IFTNode.toCfg(): ControlFlowGraph =
        ControlFlowGraphBuilder().addSingleTree(this).build()

    private infix fun ControlFlowGraph.merge(cfg: ControlFlowGraph): ControlFlowGraph =
        ControlFlowGraphBuilder().mergeUnconditionally(this).mergeUnconditionally(cfg).build()

    private infix fun IFTNode.merge(cfg: ControlFlowGraph): ControlFlowGraph =
        this.toCfg() merge cfg

    private infix fun ControlFlowGraph.merge(iftNode: IFTNode): ControlFlowGraph =
        this merge iftNode.toCfg()

    private infix fun IFTNode.merge(iftNode: IFTNode): ControlFlowGraph =
        this.toCfg() merge iftNode.toCfg()

    private fun mergeCFGsConditionally(condition: ControlFlowGraph, cfgTrue: ControlFlowGraph, cfgFalse: ControlFlowGraph): ControlFlowGraph {
        return ControlFlowGraphBuilder().mergeUnconditionally(condition).mergeConditionally(cfgTrue, cfgFalse).build()
    }

    @Test
    fun `basic expressions`() {
        val context = ExpressionContext(
            setOf("x", "y"),
            currentFunction = Function("dummy", listOf(Function.Parameter("p", Type.Number, null)), Type.Unit, listOf())
        )

        val xExpr = "x" asVarExprIn context
        val yExpr = "y" asVarExprIn context
        val pExpr = "p" asParamExprIn context
        val xVarRead = IFTNode.DummyRead("x" asVarIn context, true)
        val yVarRead = IFTNode.DummyRead("y" asVarIn context, true)
        val pParamRead = IFTNode.DummyRead("p" asParamIn context, true)

        val basic = context.createCfg(xExpr)
        val basicParam = context.createCfg(pExpr)
        val operatorTests = mapOf(

            Expression.BooleanLiteral(true) to IFTNode.Const(1),
            Expression.NumberLiteral(5) to IFTNode.Const(5),

            Expression.UnaryOperation(Expression.UnaryOperation.Kind.NOT, xExpr) to IFTNode.LogicalNegation(xVarRead),
            Expression.UnaryOperation(Expression.UnaryOperation.Kind.BIT_NOT, xExpr) to IFTNode.BitNegation(xVarRead),
            Expression.UnaryOperation(Expression.UnaryOperation.Kind.MINUS, xExpr) to IFTNode.Negation(xVarRead),
            Expression.UnaryOperation(Expression.UnaryOperation.Kind.PLUS, xExpr) to xVarRead,

            Expression.BinaryOperation(Expression.BinaryOperation.Kind.ADD, xExpr, yExpr) to IFTNode.Add(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.MULTIPLY, xExpr, yExpr) to IFTNode.Multiply(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.DIVIDE, xExpr, yExpr) to IFTNode.Divide(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.SUBTRACT, xExpr, yExpr) to IFTNode.Subtract(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.MODULO, xExpr, yExpr) to IFTNode.Modulo(xVarRead, yVarRead),

            Expression.BinaryOperation(Expression.BinaryOperation.Kind.BIT_AND, xExpr, yExpr) to IFTNode.BitAnd(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.BIT_OR, xExpr, yExpr) to IFTNode.BitOr(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.BIT_XOR, xExpr, yExpr) to IFTNode.BitXor(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.BIT_SHIFT_LEFT, xExpr, yExpr) to IFTNode.BitShiftLeft(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.BIT_SHIFT_RIGHT, xExpr, yExpr) to IFTNode.BitShiftRight(xVarRead, yVarRead),

            Expression.BinaryOperation(Expression.BinaryOperation.Kind.XOR, xExpr, yExpr) to IFTNode.LogicalXor(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.IFF, xExpr, yExpr) to IFTNode.LogicalIff(xVarRead, yVarRead),

            Expression.BinaryOperation(Expression.BinaryOperation.Kind.EQUALS, xExpr, yExpr) to IFTNode.Equals(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.NOT_EQUALS, xExpr, yExpr) to IFTNode.NotEquals(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.LESS_THAN, xExpr, yExpr) to IFTNode.LessThan(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS, xExpr, yExpr) to IFTNode.LessThanOrEquals(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.GREATER_THAN, xExpr, yExpr) to IFTNode.GreaterThan(xVarRead, yVarRead),
            Expression.BinaryOperation(Expression.BinaryOperation.Kind.GREATER_THAN_OR_EQUALS, xExpr, yExpr) to IFTNode.GreaterThanOrEquals(xVarRead, yVarRead),
        )
        assertTrue(basic hasSameStructureAs xVarRead.toCfg())
        assertTrue(basicParam hasSameStructureAs pParamRead.toCfg())

        for ((expr, iftNode) in operatorTests) {
            assertTrue(context.createCfg(expr) hasSameStructureAs iftNode.toCfg(), expr.toString())
        }
    }

    @Test
    fun `assignment`() {
        val context = ExpressionContext(
            setOf("x", "y")
        )

        val assignmentCfg = context.createCfg("x" asVarExprIn context, "y" asVarIn context) // y = x

        assertTrue(
            assignmentCfg hasSameStructureAs IFTNode.DummyWrite("y" asVarIn context, IFTNode.DummyRead("x" asVarIn context, true), true).toCfg()
        )
    }

    @Test
    fun `global variables`() {
        val context = ExpressionContext(
            setOf("x"),
            globals = setOf("x")
        )

        val read = context.createCfg("x" asVarExprIn context)
        assertTrue(
            read hasSameStructureAs
                IFTNode.DummyRead(
                    "x" asVarIn context,
                    isDirect = false,
                    isGlobal = true
                ).toCfg()
        )

        val write = context.createCfg(Expression.NumberLiteral(10), "x" asVarIn context)
        assertTrue(
            write hasSameStructureAs
                IFTNode.DummyWrite(
                    "x" asVarIn context,
                    IFTNode.Const(10),
                    isDirect = false,
                    isGlobal = true
                ).toCfg()
        )
    }

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
        val callResult = IFTNode.DummyCallResult()

        val basicCall = context.createCfg("f" asFunCallIn context) // f()
        assertTrue(
            basicCall hasSameStructureAs (
                IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IFTNode.RegisterWrite(r1, callResult)
                    merge IFTNode.RegisterRead(r1)
                )
        )

        val callAffectingVariable = context.createCfg( // x + f(), f affects x
            ("x" asVarExprIn context)
                add ("f" asFunCallIn context)
        )
        assertTrue(
            callAffectingVariable hasSameStructureAs (
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true))
                    merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IFTNode.RegisterWrite(r2, callResult)
                    merge IFTNode.Add(IFTNode.RegisterRead(r1), IFTNode.RegisterRead(r2))
                )
        )

        val callNotAffectingVariable = context.createCfg( // x + g(), g does not affect x
            ("x" asVarExprIn context)
                add ("g" asFunCallIn context)
        )
        assertTrue(
            callNotAffectingVariable hasSameStructureAs (
                IFTNode.DummyCall("g" asFunIn context, emptyList(), callResult)
                    merge IFTNode.RegisterWrite(r1, callResult)
                    merge IFTNode.Add(IFTNode.DummyRead("x" asVarIn context, true), IFTNode.RegisterRead(r1))
                )
        )

        val variableAfterAffectingFunction = context.createCfg( // f() + x, f affects x
            ("f" asFunCallIn context)
                add ("x" asVarExprIn context)
        )
        assertTrue(
            variableAfterAffectingFunction hasSameStructureAs (
                IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IFTNode.RegisterWrite(r1, callResult)
                    merge IFTNode.Add(IFTNode.RegisterRead(r1), IFTNode.DummyRead("x" asVarIn context, true))
                )
        )

        val variableAfterNotAffectingFunction = context.createCfg( // g() + x, g does not affect x
            ("g" asFunCallIn context)
                add ("x" asVarExprIn context)
        )
        assertTrue(
            variableAfterNotAffectingFunction hasSameStructureAs (
                IFTNode.DummyCall("g" asFunIn context, emptyList(), callResult)
                    merge IFTNode.RegisterWrite(r1, callResult)
                    merge IFTNode.Add(IFTNode.RegisterRead(r1), IFTNode.DummyRead("x" asVarIn context, true))
                )
        )

        val variableOnBothSidesOfFunction = context.createCfg( // x + f() + x, f -> x
            ("x" asVarExprIn context)
                add ("f" asFunCallIn context)
                add ("x" asVarExprIn context)
        )
        assertTrue(
            variableOnBothSidesOfFunction hasSameStructureAs (
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true))
                    merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IFTNode.RegisterWrite(r2, callResult)
                    merge IFTNode.Add(IFTNode.Add(IFTNode.RegisterRead(r1), IFTNode.RegisterRead(r2)), IFTNode.DummyRead("x" asVarIn context, true))
                )
        )

        val multipleUsageBeforeCall = context.createCfg( // x + x + f(), f -> x
            ("x" asVarExprIn context)
                add ("x" asVarExprIn context)
                add ("f" asFunCallIn context)
        )
        assertTrue(
            multipleUsageBeforeCall hasSameStructureAs (
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true))
                    merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                    merge IFTNode.RegisterWrite(r2, callResult)
                    merge IFTNode.Add(
                        IFTNode.Add(
                            IFTNode.RegisterRead(r1),
                            IFTNode.RegisterRead(r1)
                        ),
                        IFTNode.RegisterRead(r2)
                    )
                )
        )
    }

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
                    IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IFTNode.RegisterWrite(r1, IFTNode.DummyRead("y" asVarIn context, true)).toCfg(),
                    IFTNode.RegisterWrite(r1, IFTNode.Const(0)).toCfg()
                )
                    merge IFTNode.RegisterRead(r1)
                )
        )

        val orCfg = context.createCfg( // x lub y
            ("x" asVarExprIn context)
                or ("y" asVarExprIn context)
        )
        assertTrue(
            orCfg hasSameStructureAs (
                mergeCFGsConditionally(
                    IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IFTNode.RegisterWrite(r1, IFTNode.Const(1)).toCfg(),
                    IFTNode.RegisterWrite(r1, IFTNode.DummyRead("y" asVarIn context, true)).toCfg()
                )
                    merge IFTNode.RegisterRead(r1)
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
                    IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IFTNode.RegisterWrite(r1, IFTNode.DummyRead("y" asVarIn context, true)).toCfg(),
                    IFTNode.RegisterWrite(r1, IFTNode.DummyRead("z" asVarIn context, true)).toCfg()
                )
                    merge IFTNode.RegisterRead(r1)
                )
        )
    }

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
        val callResult1 = IFTNode.DummyCallResult()
        val callResult2 = IFTNode.DummyCallResult()
        val callResult3 = IFTNode.DummyCallResult()

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
                    IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true)).toCfg(),
                    IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true)).toCfg()
                )
                    merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                    merge IFTNode.RegisterWrite(r2, callResult1)
                    merge IFTNode.Add(IFTNode.RegisterRead(r1), IFTNode.RegisterRead(r2))
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
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true))
                    merge IFTNode.RegisterWrite(r2, IFTNode.DummyRead("y" asVarIn context, true))
                    merge IFTNode.RegisterWrite(r3, IFTNode.DummyRead("z" asVarIn context, true))
                    merge mergeCFGsConditionally(
                        IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                            merge IFTNode.RegisterWrite(r4, callResult1)
                            merge IFTNode.RegisterRead(r4),
                        IFTNode.DummyCall("g" asFunIn context, emptyList(), callResult2)
                            merge IFTNode.RegisterWrite(r5, callResult2)
                            merge IFTNode.RegisterWrite(r7, IFTNode.RegisterRead(r5)),
                        IFTNode.DummyCall("h" asFunIn context, emptyList(), callResult3)
                            merge IFTNode.RegisterWrite(r6, callResult3)
                            merge IFTNode.RegisterWrite(r7, IFTNode.RegisterRead(r6))
                    )
                    merge IFTNode.Add(
                        IFTNode.Add(
                            IFTNode.Add(
                                IFTNode.RegisterRead(r1),
                                IFTNode.RegisterRead(r2)
                            ),
                            IFTNode.RegisterRead(r3)
                        ),
                        IFTNode.RegisterRead(r7)
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
                    IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                        merge IFTNode.RegisterWrite(r2, callResult1)
                        merge IFTNode.RegisterWrite(r1, IFTNode.RegisterRead(r2)),
                    IFTNode.RegisterWrite(r1, IFTNode.Const(0)).toCfg()
                )
                    merge IFTNode.RegisterRead(r1)
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
                    IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                    IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true)).toCfg(),
                    IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                        merge IFTNode.RegisterWrite(r2, callResult1)
                        merge IFTNode.RegisterWrite(r1, IFTNode.RegisterRead(r2))
                )
                    merge IFTNode.RegisterRead(r1)
                )
        )
    }

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
        val callResult1 = IFTNode.DummyCallResult()
        val callResult2 = IFTNode.DummyCallResult()

        val read1 = IFTNode.RegisterRead(r1)
        val read2 = IFTNode.RegisterRead(r2)
        val read3 = IFTNode.RegisterRead(r3)

        val multipleArguments = context.createCfg( // g( x, f() ), f -> x
            "g" withArgs listOf("x" asVarExprIn context, "f" asFunCallIn context)
                asFunCallIn context
        )
        assertTrue(
            multipleArguments hasSameStructureAs (
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true))
                    merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                    merge IFTNode.RegisterWrite(r2, callResult1)
                    merge IFTNode.DummyCall("g" asFunIn context, listOf(read1, read2), callResult2)
                    merge IFTNode.RegisterWrite(r3, callResult2)
                    merge IFTNode.RegisterRead(r3)
                )
        )

        val nestedArguments = context.createCfg( // x + h(f()), f -> x
            ("x" asVarExprIn context)
                add ("h" withArgs listOf("f" asFunCallIn context) asFunCallIn context)
        )
        assertTrue(
            nestedArguments hasSameStructureAs (
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true))
                    merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                    merge IFTNode.RegisterWrite(r2, callResult1)
                    merge IFTNode.DummyCall("h" asFunIn context, listOf(read2), callResult2)
                    merge IFTNode.RegisterWrite(r3, callResult2)
                    merge IFTNode.Add(read1, read3)
                )
        )
    }

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
        val callResult1 = IFTNode.DummyCallResult()
        val callResult2 = IFTNode.DummyCallResult()
        val callResult3 = IFTNode.DummyCallResult()

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
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true))
                    merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                    merge IFTNode.RegisterWrite(r2, callResult1)
                    merge IFTNode.DummyCall(
                        "g" asFunIn context,
                        listOf(
                            IFTNode.Add(
                                IFTNode.DummyRead("a" asVarIn context, true),
                                IFTNode.RegisterRead(r2)
                            ),
                            IFTNode.DummyRead("b" asVarIn context, true)
                        ),
                        callResult2
                    )
                    merge IFTNode.RegisterWrite(r3, callResult2)
                    merge IFTNode.RegisterWrite(r4, IFTNode.DummyRead("b" asVarIn context, true))
                    merge IFTNode.DummyCall("h" asFunIn context, emptyList(), callResult3)
                    merge IFTNode.RegisterWrite(r5, callResult3)
                    merge mergeCFGsConditionally(
                        IFTNode.RegisterRead(r5).toCfg(),
                        IFTNode.RegisterWrite(r6, IFTNode.DummyRead("x" asVarIn context, true)).toCfg(),
                        IFTNode.RegisterWrite(r6, IFTNode.DummyRead("y" asVarIn context, true)).toCfg()
                    )
                    merge IFTNode.Add(
                        IFTNode.RegisterRead(r1),
                        IFTNode.Add(
                            IFTNode.RegisterRead(r3),
                            IFTNode.Add(
                                IFTNode.Add(
                                    IFTNode.RegisterRead(r4),
                                    IFTNode.DummyRead("x" asVarIn context, true)
                                ),
                                IFTNode.RegisterRead(r6)
                            )
                        )
                    )
                )
        )
    }
}
