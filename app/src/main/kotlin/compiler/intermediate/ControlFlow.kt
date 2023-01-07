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
import compiler.ast.Variable
import compiler.ast.VariableOwner
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.ControlFlowDiagnostic
import compiler.diagnostics.Diagnostics
import compiler.intermediate.FunctionDependenciesAnalyzer.createCallGraph
import compiler.intermediate.generators.FunctionDetailsGenerator
import compiler.intermediate.generators.GeneratorDetailsGenerator
import compiler.intermediate.generators.GlobalVariableAccessGenerator
import compiler.intermediate.generators.VariableAccessGenerator
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf
import compiler.utils.mutableRefSetOf
import compiler.utils.refSetOf

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

        fun partiallyAppliedCreateGraphForExpression(expression: Expression, targetVariable: Variable?, currentFunction: Function): ControlFlowGraph {
            return createGraphForExpression(
                expression,
                targetVariable,
                currentFunction,
                programProperties.nameResolution,
                programProperties.variableProperties,
                callGraph,
                functionDetailsGenerators,
                generatorDetailsGenerators,
                programProperties.argumentResolution,
                programProperties.defaultParameterMapping,
                globalVariableAccessGenerator
            )
        }

        fun createWriteToVariable(iftNode: IFTNode, variable: Variable, currentFunction: Function): IFTNode {
            val variableAccessGenerators: Map<Ref<VariableOwner>, VariableAccessGenerator> =
                createVariableAccessGenerators(functionDetailsGenerators, generatorDetailsGenerators, globalVariableAccessGenerator)
            val owner = programProperties.variableProperties[Ref(variable)]!!.owner
            return variableAccessGenerators[Ref(owner)]!!.genWrite(variable, iftNode, owner === currentFunction)
        }

        fun getGeneratorDetailsGenerator(generatorName: String) =
            generatorDetailsGenerators.filter { it.key.value.name == generatorName }.toList().first().second

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

    fun createGraphForExpression(
        expression: Expression,
        targetVariable: Variable?,
        currentFunction: Function,
        nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>,
        variableProperties: Map<Ref<AstNode>, VariablePropertiesAnalyzer.VariableProperties>,
        callGraph: Map<Ref<Function>, Set<Ref<Function>>>,
        functionDetailsGenerators: Map<Ref<Function>, FunctionDetailsGenerator>,
        generatorDetailsGenerators: Map<Ref<Function>, GeneratorDetailsGenerator>,
        argumentResolution: ArgumentResolutionResult,
        defaultParameterMapping: Map<Ref<Function.Parameter>, Variable>,
        globalVariablesAccessGenerator: VariableAccessGenerator
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
            }
        }

        gatherVariableUsageInfo(expression, refSetOf())

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
            }
        }

        val result = makeCFGForSubtree(expression)

        // build last tree into CFG, possibly wrapped in variable write operation
        if (targetVariable != null) {
            val owner = variableProperties[Ref(targetVariable)]!!.owner
            cfgBuilder.addNextTree(variableAccessGenerators[Ref(owner)]!!.genWrite(targetVariable, result, owner === currentFunction))
        } else {
            cfgBuilder.addNextTree(result)
        }

        return cfgBuilder.build()
    }

    fun createGraphForEachFunction(
        program: Program,
        createGraphForExpression: (Expression, Variable?, Function) -> ControlFlowGraph,
        nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>,
        defaultParameterValues: Map<Ref<Function.Parameter>, Variable>,
        functionReturnedValueVariables: Map<Ref<Function>, Variable>,
        diagnostics: Diagnostics,
        createWriteToVariable: (IFTNode, Variable, Function) -> IFTNode,
        getGeneratorDetailsGenerator: (String) -> GeneratorDetailsGenerator
    ): Map<Ref<Function>, ControlFlowGraph> {
        val controlFlowGraphs = mutableKeyRefMapOf<Function, ControlFlowGraph>()

        fun processFunction(function: Function) {
            val cfgBuilder = ControlFlowGraphBuilder()

            var last = listOf<Pair<IFTNode, CFGLinkType>?>(null)
            var breaking: MutableList<Pair<IFTNode, CFGLinkType>?>? = null
            var continuing: MutableList<Pair<IFTNode, CFGLinkType>?>? = null

            fun processStatementBlock(block: StatementBlock) {
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

                fun addExpression(expression: Expression, variable: Variable?): IFTNode? {
                    return addCfg(createGraphForExpression(expression, variable, function))
                }

                fun addLoop(conditionEntry: IFTNode, generateLoopBody: () -> Unit) {
                    val conditionEnd = last

                    val outerBreaking = breaking
                    val outerContinuing = continuing

                    breaking = mutableListOf()
                    continuing = mutableListOf()

                    last = mapLinkType(conditionEnd, CFGLinkType.CONDITIONAL_TRUE)
                    generateLoopBody()
                    val end = last

                    for (node in end + continuing!!)
                        cfgBuilder.addLink(node, conditionEntry)

                    last = mapLinkType(conditionEnd, CFGLinkType.CONDITIONAL_FALSE) + breaking!!

                    breaking = outerBreaking
                    continuing = outerContinuing
                }

                for (statement in block) {
                    if (last.isEmpty())
                        diagnostics.report(ControlFlowDiagnostic.Warnings.UnreachableStatement(statement))

                    when (statement) {
                        is Statement.Evaluation -> addExpression(statement.expression, null)

                        is Statement.VariableDefinition -> {
                            val variable = statement.variable

                            if (variable.kind != Variable.Kind.CONSTANT && variable.value != null)
                                addExpression(variable.value, variable)
                        }

                        is Statement.FunctionDefinition -> {
                            val nestedFunction = statement.function

                            for (parameter in nestedFunction.parameters) {
                                if (parameter.defaultValue != null)
                                    addExpression(parameter.defaultValue, defaultParameterValues[Ref(parameter)])
                            }

                            if (nestedFunction.implementation is Function.Implementation.Local)
                                processFunction(nestedFunction)
                        }

                        is Statement.Assignment -> addExpression(statement.value, nameResolution[Ref(statement)]!!.value as Variable)

                        is Statement.Block -> processStatementBlock(statement.block)

                        is Statement.Conditional -> {
                            addExpression(statement.condition, null)!!
                            val conditionEnd = last

                            last = mapLinkType(conditionEnd, CFGLinkType.CONDITIONAL_TRUE)
                            processStatementBlock(statement.actionWhenTrue)
                            val trueBranchEnd = last

                            last = mapLinkType(conditionEnd, CFGLinkType.CONDITIONAL_FALSE)
                            if (statement.actionWhenFalse != null)
                                processStatementBlock(statement.actionWhenFalse)
                            val falseBranchEnd = last

                            last = trueBranchEnd + falseBranchEnd
                        }

                        is Statement.Loop -> addLoop(addExpression(statement.condition, null)!!) {
                            processStatementBlock(statement.action)
                        }

                        is Statement.LoopBreak -> {
                            if (breaking != null)
                                breaking!!.addAll(last)
                            else
                                diagnostics.report(ControlFlowDiagnostic.Errors.BreakOutsideOfLoop(statement))

                            last = emptyList()
                        }

                        is Statement.LoopContinuation -> {
                            if (continuing != null)
                                continuing!!.addAll(last)
                            else
                                diagnostics.report(ControlFlowDiagnostic.Errors.ContinuationOutsideOfLoop(statement))

                            last = emptyList()
                        }

                        is Statement.FunctionReturn -> {
                            addExpression(statement.value, functionReturnedValueVariables[Ref(function)])
                            last = emptyList()
                        }

                        is Statement.ForeachLoop -> {
                            val genName = statement.generatorCall.name
                            val gdg = getGeneratorDetailsGenerator(genName)
                            addExpression(statement.generatorCall, null) // initialization
                            val generatorId = last.first()!!.first
                            val stateMemoryAddress = gdg.getNestedForeachFramePointerAddress(statement)

                            // generator state, initially 0
                            val (readState, writeState) = run {
                                if (stateMemoryAddress == null) {
                                    val lastPosition = Register()
                                    addNode(IFTNode.RegisterWrite(lastPosition, IFTNode.Const(0)))
                                    Pair(
                                        { IFTNode.RegisterRead(lastPosition) },
                                        { node: IFTNode -> IFTNode.RegisterWrite(lastPosition, node) }
                                    )
                                } else { // we need to use provided address
                                    Pair(
                                        { IFTNode.MemoryRead(stateMemoryAddress) },
                                        { node: IFTNode -> IFTNode.MemoryWrite(stateMemoryAddress, node) }
                                    )
                                }
                            }

                            val resume = gdg.genResumeCall(generatorId, readState())
                            val resumeEntry = addCfg(resume.callGraph)!!

                            addNode(
                                IFTNode.Negation(
                                    IFTNode.Equals(resume.secondResult!!, IFTNode.Const(0))
                                )
                            )
                            addLoop(resumeEntry) {
                                addNode(writeState(resume.secondResult))
                                addNode(createWriteToVariable(resume.result!!, statement.receivingVariable, function))
                                processStatementBlock(statement.action)
                            }

                            stateMemoryAddress?.let {
                                addNode(writeState(IFTNode.Const(0)))
                            }
                            addCfg(gdg.genFinalizeCall(generatorId).callGraph)
                        }

                        is Statement.GeneratorYield -> addCfg(
                            getGeneratorDetailsGenerator(function.name).genYield(
                                run {
                                    addExpression(statement.value, null)
                                    last.first()!!.first
                                }
                            )
                        )
                    }
                }
            }

            processStatementBlock(function.body)

            controlFlowGraphs[Ref(function)] = cfgBuilder.build()
        }

        program.globals
            .filterIsInstance<Program.Global.FunctionDefinition>()
            .filter { it.function.implementation is Function.Implementation.Local }
            .forEach { processFunction(it.function) }

        return controlFlowGraphs
    }
}
