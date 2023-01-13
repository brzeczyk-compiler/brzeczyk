package compiler.intermediate

import compiler.analysis.ArgumentResolutionResult
import compiler.analysis.ProgramAnalyzer
import compiler.analysis.VariablePropertiesAnalyzer
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Type
import compiler.ast.Variable
import compiler.ast.VariableOwner
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.ControlFlowDiagnostic
import compiler.diagnostics.Diagnostics
import compiler.intermediate.FunctionDependenciesAnalyzer.createCallGraph
import compiler.intermediate.generators.ArrayMemoryManagement
import compiler.intermediate.generators.FunctionDetailsGenerator
import compiler.intermediate.generators.GeneratorDetailsGenerator
import compiler.intermediate.generators.GlobalVariableAccessGenerator
import compiler.intermediate.generators.VariableAccessGenerator
import compiler.intermediate.generators.memoryUnitSize
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf
import compiler.utils.mutableRefSetOf
import compiler.utils.refSetOf
import java.util.Stack

object ControlFlow {
    private fun mapLinkType(list: List<Pair<IFTNode, CFGLinkType>?>, type: CFGLinkType) = list.map { it?.copy(second = type) }

    fun createGraphForProgram(
        program: Program,
        programProperties: ProgramAnalyzer.ProgramProperties,
        functionDetailsGenerators: Map<Ref<Function>, FunctionDetailsGenerator>,
        generatorDetailsGenerators: Map<Ref<Function>, GeneratorDetailsGenerator>,
        diagnostics: Diagnostics,
    ): Map<Ref<Function>, ControlFlowGraph> {
        val globalVariableAccessGenerator = GlobalVariableAccessGenerator(programProperties.variableProperties)
        val callGraph = createCallGraph(program, programProperties.nameResolution)

        fun partiallyAppliedCreateGraphForExpression(expression: Expression, target: AssignmentTarget?, currentFunction: Function, accessNodeConsumer: ((ControlFlowGraph, IFTNode) -> Unit)?): ControlFlowGraph {
            return createGraphForExpression(
                expression,
                target,
                currentFunction,
                programProperties.nameResolution,
                programProperties.variableProperties,
                callGraph,
                functionDetailsGenerators,
                generatorDetailsGenerators,
                programProperties.argumentResolution,
                programProperties.defaultParameterMapping,
                globalVariableAccessGenerator,
                accessNodeConsumer
            )
        }

        fun createWriteToVariable(iftNode: IFTNode, variable: Variable, currentFunction: Function): IFTNode {
            val variableAccessGenerators: Map<Ref<VariableOwner>, VariableAccessGenerator> =
                createVariableAccessGenerators(functionDetailsGenerators, generatorDetailsGenerators, globalVariableAccessGenerator)
            val owner = programProperties.variableProperties[Ref(variable)]!!.owner
            return variableAccessGenerators[Ref(owner)]!!.genWrite(variable, iftNode, owner === currentFunction)
        }

        fun getGeneratorDetailsGenerator(generator: Function) =
            generatorDetailsGenerators[Ref(generator)]!!

        val cfgForEachFunction = createGraphForEachFunction(
            program,
            ::partiallyAppliedCreateGraphForExpression,
            programProperties.nameResolution,
            programProperties.defaultParameterMapping,
            programProperties.functionReturnedValueVariables,
            diagnostics,
            ::createWriteToVariable,
            ::getGeneratorDetailsGenerator
        )

        return cfgForEachFunction.mapValues { (function, bodyCFG) ->
            attachPrologueAndEpilogue(
                bodyCFG,
                functionDetailsGenerators[function]!!.genPrologue(),
                functionDetailsGenerators[function]!!.genEpilogue()
            )
        } // TODO: add initialization and finalization of generators
    }

    fun attachPrologueAndEpilogue(body: ControlFlowGraph, prologue: ControlFlowGraph, epilogue: ControlFlowGraph): ControlFlowGraph {
        val builder = ControlFlowGraphBuilder()
        builder.addAllFrom(prologue)
        if (body.entryTreeRoot != null) {
            builder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, body.entryTreeRoot)
            builder.addAllFrom(body)
            builder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, epilogue.entryTreeRoot!!)

            for (node in body.treeRoots) {
                if (body.conditionalFalseLinks.containsKey(Ref(node)) && !body.conditionalTrueLinks.containsKey(Ref(node)))
                    builder.addLink(Pair(node, CFGLinkType.CONDITIONAL_TRUE), epilogue.entryTreeRoot)
                if (!body.conditionalFalseLinks.containsKey(Ref(node)) && body.conditionalTrueLinks.containsKey(Ref(node)))
                    builder.addLink(Pair(node, CFGLinkType.CONDITIONAL_FALSE), epilogue.entryTreeRoot)
            }
        } else
            builder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, epilogue.entryTreeRoot!!)
        builder.addAllFrom(epilogue)
        return builder.build()
    }

    private fun createVariableAccessGenerators(
        functionDetailsGenerators: Map<Ref<Function>, FunctionDetailsGenerator>,
        generatorDetailsGenerators: Map<Ref<Function>, GeneratorDetailsGenerator>,
        globalVariableAccessGenerator: VariableAccessGenerator
    ) = run {
        val result = mutableKeyRefMapOf<VariableOwner, VariableAccessGenerator>()

        result.putAll(functionDetailsGenerators)
        result.putAll(generatorDetailsGenerators)
        result[Ref(VariablePropertiesAnalyzer.GlobalContext)] = globalVariableAccessGenerator
        result
    }

    sealed class AssignmentTarget {
        data class VariableTarget(val variable: Variable) : AssignmentTarget()
        data class ArrayElementTarget(val element: Statement.Assignment.LValue.ArrayElement) : AssignmentTarget()
    }

    fun createGraphForExpression(
        expression: Expression,
        target: AssignmentTarget?,
        currentFunction: Function,
        nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>,
        variableProperties: Map<Ref<AstNode>, VariablePropertiesAnalyzer.VariableProperties>,
        callGraph: Map<Ref<Function>, Set<Ref<Function>>>,
        functionDetailsGenerators: Map<Ref<Function>, FunctionDetailsGenerator>,
        generatorDetailsGenerators: Map<Ref<Function>, GeneratorDetailsGenerator>,
        argumentResolution: ArgumentResolutionResult,
        defaultParameterMapping: Map<Ref<Function.Parameter>, Variable>,
        globalVariablesAccessGenerator: VariableAccessGenerator,
        accessNodeConsumer: ((ControlFlowGraph, IFTNode) -> Unit)? = null // if not provided, access node will be added at the end of cfg
    ): ControlFlowGraph {
        fun getVariablesModifiedBy(function: Function): Set<Ref<Variable>> {
            val possiblyCalledFunctions = callGraph[Ref(function)]!! + refSetOf(function)
            return variableProperties.asSequence()
                .filter { (it.value.writtenIn intersect possiblyCalledFunctions).isNotEmpty() }
                .map { Ref(it.key.value as Variable) }.toSet()
        }

        val variableAccessGenerators: Map<Ref<VariableOwner>, VariableAccessGenerator> =
            createVariableAccessGenerators(functionDetailsGenerators, generatorDetailsGenerators, globalVariablesAccessGenerator)

        // first stage is to decide which variable usages have to be realized via temporary registers
        // and which variables are invalidated by function calls / conditionals
        val usagesThatRequireTempRegisters = mutableRefSetOf<Expression.Variable>()
        val invalidatedVariables = mutableKeyRefMapOf<Expression, Set<Ref<Variable>>>()

        fun gatherVariableUsageInfo(astNode: Expression, modifiedUnderCurrentBase: Set<Ref<Variable>>): Set<Ref<Variable>> {
            return when (astNode) {
                is Expression.UnitLiteral,
                is Expression.BooleanLiteral,
                is Expression.NumberLiteral -> modifiedUnderCurrentBase

                is Expression.Variable -> {
                    if (nameResolution[Ref(astNode)] in modifiedUnderCurrentBase)
                        usagesThatRequireTempRegisters.add(Ref(astNode))
                    modifiedUnderCurrentBase
                }

                is Expression.UnaryOperation ->
                    gatherVariableUsageInfo(astNode.operand, modifiedUnderCurrentBase)

                is Expression.BinaryOperation -> {
                    if (astNode.kind in listOf(Expression.BinaryOperation.Kind.OR, Expression.BinaryOperation.Kind.AND)) {
                        val leftModifiedVariables = gatherVariableUsageInfo(astNode.leftOperand, refSetOf())
                        val rightModifiedVariables = gatherVariableUsageInfo(astNode.rightOperand, refSetOf())

                        invalidatedVariables[Ref(astNode)] = rightModifiedVariables
                        modifiedUnderCurrentBase + leftModifiedVariables + rightModifiedVariables
                    } else {
                        val modifiedAfterRight = gatherVariableUsageInfo(astNode.rightOperand, modifiedUnderCurrentBase)
                        gatherVariableUsageInfo(astNode.leftOperand, modifiedAfterRight)
                    }
                }

                is Expression.FunctionCall -> {
                    var modifiedInArguments: Set<Ref<Variable>> = refSetOf()
                    astNode.arguments.reversed().forEach { argumentNode ->
                        modifiedInArguments = gatherVariableUsageInfo(argumentNode.value, modifiedInArguments)
                    }

                    val modifiedByCall = getVariablesModifiedBy(nameResolution[Ref(astNode)]!!.value as Function)
                    invalidatedVariables[Ref(astNode)] = modifiedByCall
                    modifiedUnderCurrentBase + modifiedInArguments + modifiedByCall
                }

                is Expression.Conditional -> {
                    val modifiedInCondition = gatherVariableUsageInfo(astNode.condition, refSetOf())
                    val modifiedInTrueBranch = gatherVariableUsageInfo(astNode.resultWhenTrue, refSetOf())
                    val modifiedInFalseBranch = gatherVariableUsageInfo(astNode.resultWhenFalse, refSetOf())

                    invalidatedVariables[Ref(astNode)] = modifiedInTrueBranch + modifiedInFalseBranch
                    modifiedUnderCurrentBase + modifiedInCondition + modifiedInTrueBranch + modifiedInFalseBranch
                }
                is Expression.ArrayLength ->
                    gatherVariableUsageInfo(astNode.expression, modifiedUnderCurrentBase)

                is Expression.ArrayElement -> {
                    val modifiedAfterIndexEval = gatherVariableUsageInfo(astNode.index, modifiedUnderCurrentBase)
                    gatherVariableUsageInfo(astNode.expression, modifiedAfterIndexEval)
                }
                is Expression.ArrayAllocation -> {
                    var modifiedInInitList: Set<Ref<Variable>> = refSetOf()
                    astNode.initalization.reversed().forEach { expression ->
                        modifiedInInitList = gatherVariableUsageInfo(expression, modifiedInInitList)
                    }
                    modifiedUnderCurrentBase + modifiedInInitList
                }
            }
        }

        gatherVariableUsageInfo(expression, refSetOf()).let {
            if (target is AssignmentTarget.ArrayElementTarget)
                gatherVariableUsageInfo(
                    target.element.expression,
                    gatherVariableUsageInfo(target.element.index, it)
                )
        }

        // second stage is to actually produce CFG
        val cfgBuilder = ControlFlowGraphBuilder()
        var last = listOf<Pair<IFTNode, CFGLinkType>?>(null)
        var currentTemporaryRegisters = mutableKeyRefMapOf<NamedNode, Register>()

        fun ControlFlowGraphBuilder.addNextCFG(nextCFG: ControlFlowGraph) {
            if (nextCFG.entryTreeRoot != null) {
                last.forEach { addLink(it, nextCFG.entryTreeRoot) }
                last = nextCFG.finalTreeRoots
            }
            addAllFrom(nextCFG)
        }

        fun ControlFlowGraphBuilder.addNextTree(nextTree: IFTNode) {
            addNextCFG(ControlFlowGraphBuilder().apply { addLink(null, nextTree) }.build())
        }

        fun makeReadNode(readableNode: NamedNode): IFTNode {
            // readableNode must be Variable or Function.Parameter
            val owner = variableProperties[Ref(readableNode)]!!.owner
            return variableAccessGenerators[Ref(owner)]!!.genRead(readableNode, owner === currentFunction)
        }

        fun makeCFGForSubtree(astNode: Expression): IFTNode {
            return when (astNode) {
                is Expression.UnitLiteral -> IFTNode.Const(IFTNode.UNIT_VALUE)

                is Expression.BooleanLiteral -> IFTNode.Const(if (astNode.value) 1 else 0)

                is Expression.NumberLiteral -> IFTNode.Const(astNode.value)

                is Expression.Variable -> {
                    val readableNode = nameResolution[Ref(astNode)]!!.value
                    if (Ref(astNode) !in usagesThatRequireTempRegisters) {
                        makeReadNode(readableNode)
                    } else {
                        if (Ref(readableNode) !in currentTemporaryRegisters) {
                            val valueNode = makeReadNode(readableNode)
                            val temporaryRegister = Register()
                            val assignmentNode = IFTNode.RegisterWrite(temporaryRegister, valueNode)
                            currentTemporaryRegisters[Ref(readableNode)] = temporaryRegister
                            cfgBuilder.addNextTree(assignmentNode)
                        }
                        IFTNode.RegisterRead(currentTemporaryRegisters[Ref(readableNode)]!!)
                    }
                }

                is Expression.UnaryOperation -> {
                    val subtreeNode = makeCFGForSubtree(astNode.operand)
                    when (astNode.kind) {
                        Expression.UnaryOperation.Kind.NOT -> IFTNode.LogicalNegation(subtreeNode)
                        Expression.UnaryOperation.Kind.PLUS -> subtreeNode
                        Expression.UnaryOperation.Kind.MINUS -> IFTNode.Negation(subtreeNode)
                        Expression.UnaryOperation.Kind.BIT_NOT -> IFTNode.BitNegation(subtreeNode)
                    }
                }

                is Expression.BinaryOperation -> when (astNode.kind) {
                    Expression.BinaryOperation.Kind.AND, Expression.BinaryOperation.Kind.OR -> {
                        val (rightLinkType, shortCircuitLinkType, shortCircuitValue) = when (astNode.kind) {
                            Expression.BinaryOperation.Kind.AND -> Triple(CFGLinkType.CONDITIONAL_TRUE, CFGLinkType.CONDITIONAL_FALSE, 0L)
                            Expression.BinaryOperation.Kind.OR -> Triple(CFGLinkType.CONDITIONAL_FALSE, CFGLinkType.CONDITIONAL_TRUE, 1L)
                            else -> throw Exception() // unreachable state
                        }

                        cfgBuilder.addNextTree(makeCFGForSubtree(astNode.leftOperand))
                        val lastAfterLeft = last
                        val resultTemporaryRegister = Register()

                        last = mapLinkType(lastAfterLeft, rightLinkType)
                        val rightResultNode = makeCFGForSubtree(astNode.rightOperand)
                        cfgBuilder.addNextTree(IFTNode.RegisterWrite(resultTemporaryRegister, rightResultNode))
                        val lastAfterRight = last

                        last = mapLinkType(lastAfterLeft, shortCircuitLinkType)
                        val shortCircuitResultNode = IFTNode.Const(shortCircuitValue)
                        cfgBuilder.addNextTree(IFTNode.RegisterWrite(resultTemporaryRegister, shortCircuitResultNode))
                        val lastAfterShortCircuit = last

                        invalidatedVariables[Ref(astNode)]!!.forEach { currentTemporaryRegisters.remove(it) }
                        last = lastAfterRight + lastAfterShortCircuit
                        IFTNode.RegisterRead(resultTemporaryRegister)
                    }

                    else -> {
                        val leftSubtreeNode = makeCFGForSubtree(astNode.leftOperand)
                        val rightSubtreeNode = makeCFGForSubtree(astNode.rightOperand)
                        when (astNode.kind) {
                            Expression.BinaryOperation.Kind.AND,
                            Expression.BinaryOperation.Kind.OR -> throw Exception() // unreachable state
                            Expression.BinaryOperation.Kind.IFF -> IFTNode.LogicalIff(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.XOR -> IFTNode.LogicalXor(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.ADD -> IFTNode.Add(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.SUBTRACT -> IFTNode.Subtract(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.MULTIPLY -> IFTNode.Multiply(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.DIVIDE -> IFTNode.Divide(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.MODULO -> IFTNode.Modulo(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.BIT_AND -> IFTNode.BitAnd(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.BIT_OR -> IFTNode.BitOr(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.BIT_XOR -> IFTNode.BitXor(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.BIT_SHIFT_LEFT -> IFTNode.BitShiftLeft(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.BIT_SHIFT_RIGHT -> IFTNode.BitShiftRight(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.EQUALS -> IFTNode.Equals(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.NOT_EQUALS -> IFTNode.NotEquals(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.LESS_THAN -> IFTNode.LessThan(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS -> IFTNode.LessThanOrEquals(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.GREATER_THAN -> IFTNode.GreaterThan(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.GREATER_THAN_OR_EQUALS -> IFTNode.GreaterThanOrEquals(leftSubtreeNode, rightSubtreeNode)
                        }
                    }
                }

                is Expression.FunctionCall -> {
                    val function = nameResolution[Ref(astNode)]!!.value as Function
                    val parameterValues = Array<IFTNode?>(function.parameters.size) { null }

                    val explicitArgumentsResultNodes = astNode.arguments.map { makeCFGForSubtree(it.value) }
                    for ((argument, resultNode) in astNode.arguments zip explicitArgumentsResultNodes) // explicit arguments
                        parameterValues[function.parameters.indexOfFirst { Ref(it) == argumentResolution[Ref(argument)] }] = resultNode
                    function.parameters.withIndex().filter { parameterValues[it.index] == null }.forEach { // default arguments
                        parameterValues[it.index] = makeReadNode(defaultParameterMapping[Ref(it.value)]!!)
                    }
                    val callIntermediateForm = parameterValues.map { it!! }.toList().let { args ->
                        if (function.isGenerator) {
                            generatorDetailsGenerators[Ref(function)]!!.genInitCall(args)
                        } else {
                            functionDetailsGenerators[Ref(function)]!!.genCall(args)
                        }
                    }
                    cfgBuilder.addNextCFG(callIntermediateForm.callGraph)
                    invalidatedVariables[Ref(astNode)]!!.forEach { currentTemporaryRegisters.remove(it) }

                    if (callIntermediateForm.result != null) {
                        val temporaryResultRegister = Register()
                        cfgBuilder.addNextTree(IFTNode.RegisterWrite(temporaryResultRegister, callIntermediateForm.result))
                        IFTNode.RegisterRead(temporaryResultRegister)
                    } else {
                        IFTNode.Const(IFTNode.UNIT_VALUE)
                    }
                }

                is Expression.Conditional -> {
                    cfgBuilder.addNextTree(makeCFGForSubtree(astNode.condition))
                    val lastAfterCondition = last
                    val savedTemporaryRegisters = currentTemporaryRegisters.toMutableMap()
                    val resultTemporaryRegister = Register()

                    last = mapLinkType(lastAfterCondition, CFGLinkType.CONDITIONAL_TRUE)
                    val trueBranchResultNode = makeCFGForSubtree(astNode.resultWhenTrue)
                    cfgBuilder.addNextTree(IFTNode.RegisterWrite(resultTemporaryRegister, trueBranchResultNode))
                    currentTemporaryRegisters = savedTemporaryRegisters
                    val lastAfterTrueBranch = last

                    last = mapLinkType(lastAfterCondition, CFGLinkType.CONDITIONAL_FALSE)
                    val falseBranchResultNode = makeCFGForSubtree(astNode.resultWhenFalse)
                    cfgBuilder.addNextTree(IFTNode.RegisterWrite(resultTemporaryRegister, falseBranchResultNode))
                    val lastAfterFalseBranch = last

                    invalidatedVariables[Ref(astNode)]!!.forEach { currentTemporaryRegisters.remove(it) }
                    last = lastAfterTrueBranch + lastAfterFalseBranch
                    IFTNode.RegisterRead(resultTemporaryRegister)
                }
                is Expression.ArrayLength -> {
                    val array = makeCFGForSubtree(astNode.expression)
                    IFTNode.MemoryRead(IFTNode.Subtract(array, IFTNode.Const(memoryUnitSize.toLong())))
                }
                is Expression.ArrayElement -> {
                    val array = makeCFGForSubtree(astNode.expression)
                    val index = makeCFGForSubtree(astNode.index)
                    val reg = Register()
                    cfgBuilder.addNextTree(
                        IFTNode.RegisterWrite(
                            reg,
                            IFTNode.MemoryRead(
                                IFTNode.Add(array, IFTNode.Multiply(index, IFTNode.Const(memoryUnitSize.toLong())))
                            )
                        )
                    )
                    IFTNode.RegisterRead(reg)
                }
                is Expression.ArrayAllocation -> {
                    val expressions = astNode.initalization.map { makeCFGForSubtree(it) }
                    val allocation = ArrayMemoryManagement.genAllocation(astNode.size, expressions)
                    cfgBuilder.addNextCFG(allocation.first)
                    allocation.second
                }
            }
        }

        val (targetArray, index) = if (target is AssignmentTarget.ArrayElementTarget)
            Pair(makeCFGForSubtree(target.element.expression), makeCFGForSubtree(target.element.index))
        else Pair(null, null)
        val result = makeCFGForSubtree(expression)

        // build last tree into CFG, possibly wrapped in variable write operation
        if (target != null) {
            when (target) {
                is AssignmentTarget.VariableTarget -> {
                    val owner = variableProperties[Ref(target.variable)]!!.owner
                    cfgBuilder.addNextTree(variableAccessGenerators[Ref(owner)]!!.genWrite(target.variable, result, owner === currentFunction))
                }
                is AssignmentTarget.ArrayElementTarget -> {
                    cfgBuilder.addNextTree(
                        IFTNode.MemoryWrite(
                            IFTNode.Add(
                                targetArray!!,
                                IFTNode.Multiply(index!!, IFTNode.Const(memoryUnitSize.toLong()))
                            ),
                            result
                        )
                    )
                }
            }
        } else if (accessNodeConsumer == null) {
            cfgBuilder.addNextTree(result)
        }

        return cfgBuilder.build().also {
            if (accessNodeConsumer != null) {
                accessNodeConsumer(it, result)
            }
        }
    }

    private data class EscapeTreeHead(var value: IFTNode) // wrapper class allowing to modify arguments value
    private fun EscapeTreeHead?.copy() = this?.copy()

    private fun Variable.generateDestructor(): ControlFlowGraph = when (type) {
        is Type.Array -> {
            TODO()
        }
        else -> ControlFlowGraphBuilder().build()
    }

    fun createGraphForEachFunction(
        program: Program,
        createGraphForExpression: (Expression, AssignmentTarget?, Function, ((ControlFlowGraph, IFTNode) -> Unit)?) -> ControlFlowGraph,
        nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>,
        defaultParameterValues: Map<Ref<Function.Parameter>, Variable>,
        functionReturnedValueVariables: Map<Ref<Function>, Variable>,
        diagnostics: Diagnostics,
        createWriteToVariable: (IFTNode, Variable, Function) -> IFTNode,
        getGeneratorDetailsGenerator: (Function) -> GeneratorDetailsGenerator
    ): Map<Ref<Function>, ControlFlowGraph> {
        val controlFlowGraphs = mutableKeyRefMapOf<Function, ControlFlowGraph>()

        fun Variable?.asTarget() = this?.let { AssignmentTarget.VariableTarget(it) }

        fun processFunction(function: Function) {
            val cfgBuilder = ControlFlowGraphBuilder()
            var last = listOf<Pair<IFTNode, CFGLinkType>?>(null)

            fun addCfg(cfg: ControlFlowGraph): IFTNode? {
                val entry = cfg.entryTreeRoot
                if (entry != null) {
                    for (node in last) {
                        cfgBuilder.addLink(node, entry)
                    }

                    last = cfg.finalTreeRoots
                }
                cfgBuilder.addAllFrom(cfg)
                return entry
            }

            fun addNode(node: IFTNode) {
                addCfg(ControlFlowGraphBuilder().addSingleTree(node).build())
            }

            fun addDetachedNode(node: IFTNode) {
                cfgBuilder.addAllFrom(ControlFlowGraphBuilder().addSingleTree(node).build(), false)
            }

            fun processStatementBlock(block: StatementBlock, returnTreeHead: EscapeTreeHead, breakTreeHead: EscapeTreeHead?, continueTreeHead: EscapeTreeHead?) {
                val destructorsStack = Stack<ControlFlowGraph>()

                fun addToEscapeTree(value: ControlFlowGraph, treeHead: EscapeTreeHead?) {
                    if (treeHead == null || value.entryTreeRoot == null)
                        return
                    cfgBuilder.addAllFrom(value)
                    value.finalTreeRoots.forEach { cfgBuilder.addLink(it, treeHead.value, false) }
                    treeHead.value = value.entryTreeRoot
                }

                fun addToDestructionStructures(variable: Variable) {
                    destructorsStack.add(variable.generateDestructor())
                    addToEscapeTree(variable.generateDestructor(), returnTreeHead)
                    addToEscapeTree(variable.generateDestructor(), breakTreeHead)
                    addToEscapeTree(variable.generateDestructor(), continueTreeHead)
                }

                fun addExpression(expression: Expression, target: AssignmentTarget?, accessNodeConsumer: ((ControlFlowGraph, IFTNode) -> Unit)? = null): IFTNode? {
                    return if (accessNodeConsumer == null)
                        addCfg(createGraphForExpression(expression, target, function, null))
                    else run {
                        var result: IFTNode? = null
                        createGraphForExpression(expression, target, function) { cfg, node ->
                            result = addCfg(cfg)
                            accessNodeConsumer(cfg, node)
                        }
                        result
                    }
                }

                // this function doesn't modify `last`, so it has to be done manually after use
                fun addLinksFromLast(target: IFTNode) {
                    for (node in last)
                        cfgBuilder.addLink(node, target)
                }

                fun addLoop(conditionEntry: IFTNode, generateLoopBody: (EscapeTreeHead, EscapeTreeHead) -> Unit) {
                    val conditionEnd = last

                    val finalNoOp = IFTNode.NoOp()
                    addDetachedNode(finalNoOp)
                    val innerBreakTreeHead = EscapeTreeHead(finalNoOp)
                    val innerContinueTreeHead = EscapeTreeHead(conditionEntry)

                    last = mapLinkType(conditionEnd, CFGLinkType.CONDITIONAL_TRUE)
                    generateLoopBody(innerBreakTreeHead, innerContinueTreeHead)

                    last.forEach { cfgBuilder.addLink(it, conditionEntry) }
                    last = mapLinkType(conditionEnd, CFGLinkType.CONDITIONAL_FALSE)
                    addNode(finalNoOp)
                }

                for (statement in block) {
                    if (last.isEmpty())
                        diagnostics.report(ControlFlowDiagnostic.Warnings.UnreachableStatement(statement))

                    when (statement) {
                        is Statement.Evaluation -> addExpression(statement.expression, null)

                        is Statement.VariableDefinition -> {
                            val variable = statement.variable

                            if (variable.kind != Variable.Kind.CONSTANT && variable.value != null)
                                addExpression(variable.value, variable.asTarget())

                            addToDestructionStructures(variable)
                        }

                        is Statement.FunctionDefinition -> {
                            val nestedFunction = statement.function

                            for (parameter in nestedFunction.parameters) {
                                if (parameter.defaultValue != null) {
                                    val variable = defaultParameterValues[Ref(parameter)]!!
                                    addExpression(parameter.defaultValue, variable.asTarget())
                                    addToDestructionStructures(variable)
                                }
                            }

                            if (nestedFunction.implementation is Function.Implementation.Local)
                                processFunction(nestedFunction)
                        }

                        is Statement.Assignment -> addExpression(
                            statement.value,
                            when (statement.lvalue) {
                                is Statement.Assignment.LValue.Variable -> (nameResolution[Ref(statement)]!!.value as Variable).asTarget()
                                is Statement.Assignment.LValue.ArrayElement -> AssignmentTarget.ArrayElementTarget(statement.lvalue)
                            }
                        )

                        is Statement.Block -> processStatementBlock(statement.block, returnTreeHead.copy(), breakTreeHead.copy(), continueTreeHead.copy())

                        is Statement.Conditional -> {
                            addExpression(statement.condition, null)!!
                            val conditionEnd = last

                            last = mapLinkType(conditionEnd, CFGLinkType.CONDITIONAL_TRUE)
                            processStatementBlock(statement.actionWhenTrue, returnTreeHead.copy(), breakTreeHead.copy(), continueTreeHead.copy())
                            val trueBranchEnd = last

                            last = mapLinkType(conditionEnd, CFGLinkType.CONDITIONAL_FALSE)
                            if (statement.actionWhenFalse != null)
                                processStatementBlock(statement.actionWhenFalse, returnTreeHead.copy(), breakTreeHead.copy(), continueTreeHead.copy())
                            val falseBranchEnd = last

                            last = trueBranchEnd + falseBranchEnd
                        }

                        is Statement.Loop -> addLoop(addExpression(statement.condition, null)!!) { innerBreakTreeHead, innerContinueTreeHead ->
                            processStatementBlock(statement.action, returnTreeHead.copy(), innerBreakTreeHead, innerContinueTreeHead)
                        }

                        is Statement.LoopBreak -> {
                            if (breakTreeHead != null)
                                addLinksFromLast(breakTreeHead.value)
                            else
                                diagnostics.report(ControlFlowDiagnostic.Errors.BreakOutsideOfLoop(statement))

                            last = emptyList()
                        }

                        is Statement.LoopContinuation -> {
                            if (continueTreeHead != null)
                                addLinksFromLast(continueTreeHead.value)
                            else
                                diagnostics.report(ControlFlowDiagnostic.Errors.ContinuationOutsideOfLoop(statement))

                            last = emptyList()
                        }

                        is Statement.FunctionReturn -> {
                            addExpression(statement.value, functionReturnedValueVariables[Ref(function)].asTarget())
                            addLinksFromLast(returnTreeHead.value)
                            last = emptyList()
                        }

                        is Statement.ForeachLoop -> {
                            val gdg = getGeneratorDetailsGenerator(nameResolution[Ref(statement.generatorCall)]!!.value as Function)
                            val frameMemoryAddress = if (function.isGenerator)
                                getGeneratorDetailsGenerator(function).getNestedForeachFramePointerAddress(statement)
                            else null

                            val generatorId = run {
                                var result: IFTNode? = null
                                addExpression(statement.generatorCall, null) { _, node -> // initialization
                                    result = if (frameMemoryAddress == null) {
                                        val idReg = Register()
                                        addNode(IFTNode.RegisterWrite(idReg, node))
                                        IFTNode.RegisterRead(idReg)
                                    } else {
                                        addNode(IFTNode.MemoryWrite(frameMemoryAddress, node))
                                        IFTNode.MemoryRead(frameMemoryAddress)
                                    }
                                }
                                result!!
                            }

                            // generator state, initially 0
                            val lastPosition = Register()
                            addNode(IFTNode.RegisterWrite(lastPosition, IFTNode.Const(0)))

                            // loop body entry
                            val resume = gdg.genResumeCall(generatorId, IFTNode.RegisterRead(lastPosition))
                            val resumeEntry = addCfg(resume.callGraph)!!
                            addNode(IFTNode.NotEquals(resume.secondResult!!, IFTNode.Const(0)))

                            // finalization
                            fun getFinalizeCFG(): ControlFlowGraph {
                                val finalizeCfgBuilder = ControlFlowGraphBuilder()

                                finalizeCfgBuilder.addAllFrom(gdg.genFinalizeCall(generatorId).callGraph)
                                frameMemoryAddress?.let {
                                    finalizeCfgBuilder.addSingleTree(IFTNode.MemoryWrite(frameMemoryAddress, IFTNode.Const(0)))
                                }

                                return finalizeCfgBuilder.build()
                            }

                            // the loop proper
                            addLoop(resumeEntry) { innerBreakTreeHead, innerContinueTreeHead ->
                                addNode(IFTNode.RegisterWrite(lastPosition, resume.secondResult))
                                addNode(createWriteToVariable(resume.result!!, statement.receivingVariable, function))
                                val innerReturnTreeHead = returnTreeHead.copy()
                                addToEscapeTree(getFinalizeCFG(), innerReturnTreeHead)
                                processStatementBlock(statement.action, innerReturnTreeHead, innerBreakTreeHead, innerContinueTreeHead)
                            }

                            // finalize after generator end / break
                            addCfg(getFinalizeCFG())
                        }

                        is Statement.GeneratorYield -> {
                            addExpression(statement.value, null) { _, node ->
                                addCfg(getGeneratorDetailsGenerator(function).genYield(node))
                            }
                        }
                    }
                }

                while (destructorsStack.isNotEmpty())
                    addCfg(destructorsStack.pop())
            }

            val returnTreeRoot = IFTNode.NoOp()
            addDetachedNode(returnTreeRoot)
            processStatementBlock(function.body, EscapeTreeHead(returnTreeRoot), null, null)
            addNode(returnTreeRoot)

            controlFlowGraphs[Ref(function)] = cfgBuilder.build()
        }

        program.globals
            .filterIsInstance<Program.Global.FunctionDefinition>()
            .filter { it.function.implementation is Function.Implementation.Local }
            .forEach { processFunction(it.function) }

        return controlFlowGraphs
    }
}
