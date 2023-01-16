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
import compiler.intermediate.generators.memoryUnitSize
import compiler.utils.Ref
import compiler.utils.keyRefMapOf
import compiler.utils.mutableKeyRefMapOf
import compiler.utils.mutableRefMapOf
import compiler.utils.refSetOf
import kotlin.test.Test

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

        fun createCfg(expr: Expression, targetVariable: Variable? = null, expressionTypes: Map<Ref<Expression>, Type> = emptyMap()): ControlFlowGraph {
            return ControlFlow.createGraphForExpression(
                expr,
                targetVariable?.let { ControlFlow.AssignmentTarget.VariableTarget(it) },
                currentFunction,
                nameResolution,
                expressionTypes,
                variableProperties,
                finalCallGraph,
                functionDetailsGenerators,
                emptyMap(),
                argumentResolution,
                keyRefMapOf(),
                object : VariableAccessGenerator {
                    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode =
                        IFTNode.DummyRead(namedNode, isDirect, true)

                    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode =
                        IFTNode.DummyWrite(namedNode, value, isDirect, true)
                },
                TestArrayMemoryManagement()
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
        basic assertHasSameStructureAs xVarRead.toCfg()
        basicParam assertHasSameStructureAs pParamRead.toCfg()

        for ((expr, iftNode) in operatorTests) {
            context.createCfg(expr) assertHasSameStructureAs iftNode.toCfg()
        }
    }

    @Test
    fun `assignment to variables`() {
        val context = ExpressionContext(
            setOf("x", "y")
        )

        val assignmentCfg = context.createCfg("x" asVarExprIn context, "y" asVarIn context) // y = x

        assignmentCfg assertHasSameStructureAs IFTNode.DummyWrite("y" asVarIn context, IFTNode.DummyRead("x" asVarIn context, true), true).toCfg()
    }

    @Test
    fun `global variables`() {
        val context = ExpressionContext(
            setOf("x"),
            globals = setOf("x")
        )

        val read = context.createCfg("x" asVarExprIn context)
        read assertHasSameStructureAs
            IFTNode.DummyRead(
                "x" asVarIn context,
                isDirect = false,
                isGlobal = true
            ).toCfg()

        val write = context.createCfg(Expression.NumberLiteral(10), "x" asVarIn context)
        write assertHasSameStructureAs
            IFTNode.DummyWrite(
                "x" asVarIn context,
                IFTNode.Const(10),
                isDirect = false,
                isGlobal = true
            ).toCfg()
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
        basicCall assertHasSameStructureAs (
            IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                merge IFTNode.RegisterWrite(r1, callResult)
                merge IFTNode.RegisterRead(r1)
            )

        val callAffectingVariable = context.createCfg( // x + f(), f affects x
            ("x" asVarExprIn context)
                add ("f" asFunCallIn context)
        )
        callAffectingVariable assertHasSameStructureAs (
            IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true))
                merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                merge IFTNode.RegisterWrite(r2, callResult)
                merge IFTNode.Add(IFTNode.RegisterRead(r1), IFTNode.RegisterRead(r2))
            )

        val callNotAffectingVariable = context.createCfg( // x + g(), g does not affect x
            ("x" asVarExprIn context)
                add ("g" asFunCallIn context)
        )
        callNotAffectingVariable assertHasSameStructureAs (
            IFTNode.DummyCall("g" asFunIn context, emptyList(), callResult)
                merge IFTNode.RegisterWrite(r1, callResult)
                merge IFTNode.Add(IFTNode.DummyRead("x" asVarIn context, true), IFTNode.RegisterRead(r1))
            )

        val variableAfterAffectingFunction = context.createCfg( // f() + x, f affects x
            ("f" asFunCallIn context)
                add ("x" asVarExprIn context)
        )
        variableAfterAffectingFunction assertHasSameStructureAs (
            IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                merge IFTNode.RegisterWrite(r1, callResult)
                merge IFTNode.Add(IFTNode.RegisterRead(r1), IFTNode.DummyRead("x" asVarIn context, true))
            )

        val variableAfterNotAffectingFunction = context.createCfg( // g() + x, g does not affect x
            ("g" asFunCallIn context)
                add ("x" asVarExprIn context)
        )
        variableAfterNotAffectingFunction assertHasSameStructureAs (
            IFTNode.DummyCall("g" asFunIn context, emptyList(), callResult)
                merge IFTNode.RegisterWrite(r1, callResult)
                merge IFTNode.Add(IFTNode.RegisterRead(r1), IFTNode.DummyRead("x" asVarIn context, true))
            )

        val variableOnBothSidesOfFunction = context.createCfg( // x + f() + x, f -> x
            ("x" asVarExprIn context)
                add ("f" asFunCallIn context)
                add ("x" asVarExprIn context)
        )
        variableOnBothSidesOfFunction assertHasSameStructureAs (
            IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true))
                merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult)
                merge IFTNode.RegisterWrite(r2, callResult)
                merge IFTNode.Add(IFTNode.Add(IFTNode.RegisterRead(r1), IFTNode.RegisterRead(r2)), IFTNode.DummyRead("x" asVarIn context, true))
            )

        val multipleUsageBeforeCall = context.createCfg( // x + x + f(), f -> x
            ("x" asVarExprIn context)
                add ("x" asVarExprIn context)
                add ("f" asFunCallIn context)
        )
        multipleUsageBeforeCall assertHasSameStructureAs (
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
    }

    @Test
    fun `conditional expressions`() {
        val context = ExpressionContext(
            setOf("x", "y", "z")
        )

        val r1 = Register()

        val andCfg = context.createCfg( // x oraz y
            ("x" asVarExprIn context)
                and ("y" asVarExprIn context)
        )
        andCfg assertHasSameStructureAs (
            mergeCFGsConditionally(
                IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("y" asVarIn context, true)).toCfg(),
                IFTNode.RegisterWrite(r1, IFTNode.Const(0)).toCfg()
            )
                merge IFTNode.RegisterRead(r1)
            )

        val orCfg = context.createCfg( // x lub y
            ("x" asVarExprIn context)
                or ("y" asVarExprIn context)
        )
        orCfg assertHasSameStructureAs (
            mergeCFGsConditionally(
                IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                IFTNode.RegisterWrite(r1, IFTNode.Const(1)).toCfg(),
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("y" asVarIn context, true)).toCfg()
            )
                merge IFTNode.RegisterRead(r1)
            )

        val ternaryCfg = context.createCfg( // x ? y : z
            ternary(
                "x" asVarExprIn context,
                "y" asVarExprIn context,
                "z" asVarExprIn context
            )
        )
        ternaryCfg assertHasSameStructureAs (
            mergeCFGsConditionally(
                IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("y" asVarIn context, true)).toCfg(),
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("z" asVarIn context, true)).toCfg()
            )
                merge IFTNode.RegisterRead(r1)
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

        variableInConditional assertHasSameStructureAs (
            mergeCFGsConditionally(
                IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true)).toCfg(),
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true)).toCfg()
            )
                merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                merge IFTNode.RegisterWrite(r2, callResult1)
                merge IFTNode.Add(IFTNode.RegisterRead(r1), IFTNode.RegisterRead(r2))
            )

        val functionCallsInConditional = context.createCfg( // x + y + z + ( f() ? g() : h() ), f -> x, g -> y, h -> z
            ("x" asVarExprIn context) add ("y" asVarExprIn context) add ("z" asVarExprIn context) add
                ternary(
                    "f" asFunCallIn context,
                    "g" asFunCallIn context,
                    "h" asFunCallIn context,
                )
        )
        functionCallsInConditional assertHasSameStructureAs (
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

        val andWithFunction = context.createCfg( // x oraz f(), f -> x
            ("x" asVarExprIn context)
                and ("f" asFunCallIn context)
        )
        andWithFunction assertHasSameStructureAs (
            mergeCFGsConditionally(
                IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                    merge IFTNode.RegisterWrite(r2, callResult1)
                    merge IFTNode.RegisterWrite(r1, IFTNode.RegisterRead(r2)),
                IFTNode.RegisterWrite(r1, IFTNode.Const(0)).toCfg()
            )
                merge IFTNode.RegisterRead(r1)
            )

        val variableAndFunctionInTernary = context.createCfg( // x ? x : f(), f -> x
            ternary(
                "x" asVarExprIn context,
                "x" asVarExprIn context,
                "f" asFunCallIn context
            )
        )
        variableAndFunctionInTernary assertHasSameStructureAs (
            mergeCFGsConditionally(
                IFTNode.DummyRead("x" asVarIn context, true).toCfg(),
                IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true)).toCfg(),
                IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                    merge IFTNode.RegisterWrite(r2, callResult1)
                    merge IFTNode.RegisterWrite(r1, IFTNode.RegisterRead(r2))
            )
                merge IFTNode.RegisterRead(r1)
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
        multipleArguments assertHasSameStructureAs (
            IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true))
                merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                merge IFTNode.RegisterWrite(r2, callResult1)
                merge IFTNode.DummyCall("g" asFunIn context, listOf(read1, read2), callResult2)
                merge IFTNode.RegisterWrite(r3, callResult2)
                merge IFTNode.RegisterRead(r3)
            )

        val nestedArguments = context.createCfg( // x + h(f()), f -> x
            ("x" asVarExprIn context)
                add ("h" withArgs listOf("f" asFunCallIn context) asFunCallIn context)
        )
        nestedArguments assertHasSameStructureAs (
            IFTNode.RegisterWrite(r1, IFTNode.DummyRead("x" asVarIn context, true))
                merge IFTNode.DummyCall("f" asFunIn context, emptyList(), callResult1)
                merge IFTNode.RegisterWrite(r2, callResult1)
                merge IFTNode.DummyCall("h" asFunIn context, listOf(read2), callResult2)
                merge IFTNode.RegisterWrite(r3, callResult2)
                merge IFTNode.Add(read1, read3)
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

        cfg assertHasSameStructureAs (
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
    }

    private fun Int.toLiteral() = Expression.NumberLiteral(this.toLong())
    private fun Int.toConst() = IFTNode.Const(this.toLong())

    @Test
    fun `simple array`() {
        val context = ExpressionContext(setOf("a"))
        val init = Expression.ArrayAllocation(Type.Number, 5.toLiteral(), listOf(6.toLiteral()), Expression.ArrayAllocation.InitializationType.ONE_VALUE)
        val type = Type.Array(Type.Number)
        val cfgAssign = context.createCfg(init, "a" asVarIn context, expressionTypes = mapOf(Ref(init) to type)) // a = new int[5](6)
        val cfgNotAssign = context.createCfg(init, expressionTypes = mapOf(Ref(init) to type)) // new int[5](6)
        val address = dummyArrayAddress(0)

        cfgAssign assertHasSameStructureAs (
            IFTNode.DummyArrayAllocation(5.toConst(), listOf(6.toConst()), type, Expression.ArrayAllocation.InitializationType.ONE_VALUE)
                merge IFTNode.DummyWrite("a" asVarIn context, address, true)
            )
        cfgNotAssign assertHasSameStructureAs (
            IFTNode.DummyArrayAllocation(5.toConst(), listOf(6.toConst()), type, Expression.ArrayAllocation.InitializationType.ONE_VALUE)
                merge IFTNode.DummyArrayRefCountDec(address, type)
                merge address
            )
    }

    @Test
    fun `array of arrays`() {
        val context = ExpressionContext(setOf("a"))
        val alloc1 = Expression.ArrayAllocation(Type.Number, 1.toLiteral(), listOf(1.toLiteral()), Expression.ArrayAllocation.InitializationType.ONE_VALUE)
        val alloc2 = Expression.ArrayAllocation(Type.Array(Type.Number), 1.toLiteral(), listOf(alloc1), Expression.ArrayAllocation.InitializationType.ONE_VALUE)
        val type1 = Type.Array(Type.Number)
        val type2 = Type.Array(type1)
        val expressionTypes = mapOf(
            Ref(alloc1 as Expression) to type1,
            Ref(alloc2 as Expression) to type2
        )
        val cfgAssign = context.createCfg(alloc2, "a" asVarIn context, expressionTypes = expressionTypes) // a = new array<int>[1](new int[1](1))
        val cfgNotAssign = context.createCfg(alloc2, expressionTypes = expressionTypes) // new array<int>[1](new int[1](1))
        val address0 = dummyArrayAddress(0)
        val address1 = dummyArrayAddress(1)

        cfgAssign assertHasSameStructureAs (
            IFTNode.DummyArrayAllocation(1.toConst(), listOf(1.toConst()), type1, Expression.ArrayAllocation.InitializationType.ONE_VALUE)
                merge IFTNode.DummyArrayAllocation(1.toConst(), listOf(address0), type2, Expression.ArrayAllocation.InitializationType.ONE_VALUE)
                merge IFTNode.DummyArrayRefCountDec(address0, type1) // asserts refcount will be increased while allocation of second array
                merge IFTNode.DummyWrite("a" asVarIn context, address1, true)
            )
        cfgNotAssign assertHasSameStructureAs (
            IFTNode.DummyArrayAllocation(1.toConst(), listOf(1.toConst()), type1, Expression.ArrayAllocation.InitializationType.ONE_VALUE)
                merge IFTNode.DummyArrayAllocation(1.toConst(), listOf(address0), type2, Expression.ArrayAllocation.InitializationType.ONE_VALUE)
                merge IFTNode.DummyArrayRefCountDec(address0, type1)
                merge IFTNode.DummyArrayRefCountDec(address1, type2)
                merge address1
            )
    }

    @Test
    fun `arrays - access element and length`() {
        val context = ExpressionContext(emptySet())
        val init = Expression.ArrayAllocation(Type.Number, 5.toLiteral(), listOf(6.toLiteral()), Expression.ArrayAllocation.InitializationType.ONE_VALUE)
        val type = Type.Array(Type.Number)
        val getElement = Expression.ArrayElement(init, 3.toLiteral())
        val getLength = Expression.ArrayLength(init)
        val cfgElement = context.createCfg(getElement, expressionTypes = mapOf(Ref(init) to type)) // (new int[5](6))[3]
        val cfgLength = context.createCfg(getLength, expressionTypes = mapOf(Ref(init) to type)) // length(new int[5](6))
        val address = dummyArrayAddress(0)
        val resultRegister = Register()
        val arrayTempRegister = Register()

        val expectedCfgElement = (
            IFTNode.DummyArrayAllocation(5.toConst(), listOf(6.toConst()), type, Expression.ArrayAllocation.InitializationType.ONE_VALUE)
                merge IFTNode.RegisterWrite(arrayTempRegister, address)
                merge IFTNode.RegisterWrite(resultRegister, IFTNode.MemoryRead(IFTNode.Add(IFTNode.RegisterRead(arrayTempRegister), IFTNode.Multiply(3.toConst(), memoryUnitSize.toInt().toConst()))))
                merge IFTNode.DummyArrayRefCountDec(IFTNode.RegisterRead(arrayTempRegister), type)
                merge IFTNode.RegisterRead(resultRegister)
            )
        expectedCfgElement assertHasSameStructureAs cfgElement

        val expectedCfgLength = (
            IFTNode.DummyArrayAllocation(5.toConst(), listOf(6.toConst()), type, Expression.ArrayAllocation.InitializationType.ONE_VALUE)
                merge IFTNode.RegisterWrite(resultRegister, IFTNode.MemoryRead(IFTNode.Subtract(address, memoryUnitSize.toInt().toConst())))
                merge IFTNode.DummyArrayRefCountDec(address, type)
                merge IFTNode.RegisterRead(resultRegister)
            )
        expectedCfgLength assertHasSameStructureAs cfgLength
    }
}
