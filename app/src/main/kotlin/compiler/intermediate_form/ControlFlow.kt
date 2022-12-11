package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostic.ResolutionDiagnostic.ControlFlowDiagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.common.intermediate_form.FunctionDetailsGenerator
import compiler.common.intermediate_form.VariableAccessGenerator
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.ReferenceSet
import compiler.common.reference_collections.combineReferenceSets
import compiler.common.reference_collections.copy
import compiler.common.reference_collections.referenceHashMapOf
import compiler.common.reference_collections.referenceHashSetOf
import compiler.common.semantic_analysis.VariablesOwner
import compiler.semantic_analysis.ArgumentResolutionResult
import compiler.semantic_analysis.VariablePropertiesAnalyzer

object ControlFlow {
    private fun mapLinkType(list: List<Pair<IFTNode, CFGLinkType>?>, type: CFGLinkType) = list.map { it?.copy(second = type) }

    fun createGraphForExpression(
        expression: Expression,
        targetVariable: Variable?,
        currentFunction: Function,
        nameResolution: ReferenceMap<Any, NamedNode>,
        variableProperties: ReferenceMap<Any, VariablePropertiesAnalyzer.VariableProperties>,
        callGraph: ReferenceMap<Function, ReferenceSet<Function>>,
        functionDetailsGenerators: ReferenceMap<Function, FunctionDetailsGenerator>,
        argumentResolution: ArgumentResolutionResult,
        defaultParameterValues: ReferenceMap<Function.Parameter, Variable>,
        globalVariablesAccessGenerator: VariableAccessGenerator
    ): ControlFlowGraph {
        fun getVariablesModifiedBy(function: Function): ReferenceSet<Variable> {
            val possiblyCalledFunctions = combineReferenceSets(callGraph[function]!!, referenceHashSetOf(function))
            return referenceHashSetOf(
                variableProperties.asSequence()
                    .filter { (it.value.writtenIn intersect possiblyCalledFunctions).isNotEmpty() }
                    .map { it.key as Variable }.toList()
            )
        }

        val variableAccessGenerators: ReferenceMap<VariablesOwner, VariableAccessGenerator> = run {
            val result = referenceHashMapOf<VariablesOwner, VariableAccessGenerator>()

            result.putAll(functionDetailsGenerators)
            result[VariablePropertiesAnalyzer.GlobalContext] = globalVariablesAccessGenerator
            result
        }

        // first stage is to decide which variable usages have to be realized via temporary registers
        // and which variables are invalidated by function calls / conditionals
        val usagesThatRequireTempRegisters = referenceHashSetOf<Expression.Variable>()
        val invalidatedVariables = referenceHashMapOf<Expression, ReferenceSet<Variable>>()

        fun gatherVariableUsageInfo(astNode: Expression, modifiedUnderCurrentBase: ReferenceSet<Variable>): ReferenceSet<Variable> {
            return when (astNode) {
                is Expression.UnitLiteral,
                is Expression.BooleanLiteral,
                is Expression.NumberLiteral -> modifiedUnderCurrentBase

                is Expression.Variable -> {
                    if (nameResolution[astNode] in modifiedUnderCurrentBase)
                        usagesThatRequireTempRegisters.add(astNode)
                    modifiedUnderCurrentBase
                }

                is Expression.UnaryOperation ->
                    gatherVariableUsageInfo(astNode.operand, modifiedUnderCurrentBase)

                is Expression.BinaryOperation -> {
                    if (astNode.kind in listOf(Expression.BinaryOperation.Kind.OR, Expression.BinaryOperation.Kind.AND)) {
                        val leftModifiedVariables = gatherVariableUsageInfo(astNode.leftOperand, referenceHashSetOf())
                        val rightModifiedVariables = gatherVariableUsageInfo(astNode.rightOperand, referenceHashSetOf())

                        invalidatedVariables[astNode] = rightModifiedVariables
                        combineReferenceSets(modifiedUnderCurrentBase, leftModifiedVariables, rightModifiedVariables)
                    } else {
                        val modifiedAfterRight = gatherVariableUsageInfo(astNode.rightOperand, modifiedUnderCurrentBase)
                        gatherVariableUsageInfo(astNode.leftOperand, modifiedAfterRight)
                    }
                }

                is Expression.FunctionCall -> {
                    var modifiedInArguments: ReferenceSet<Variable> = referenceHashSetOf()
                    astNode.arguments.reversed().forEach { argumentNode ->
                        modifiedInArguments = gatherVariableUsageInfo(argumentNode.value, modifiedInArguments)
                    }

                    val modifiedByCall = getVariablesModifiedBy(nameResolution[astNode] as Function)
                    invalidatedVariables[astNode] = modifiedByCall
                    combineReferenceSets(modifiedUnderCurrentBase, modifiedInArguments, modifiedByCall)
                }

                is Expression.Conditional -> {
                    val modifiedInCondition = gatherVariableUsageInfo(astNode.condition, referenceHashSetOf())
                    val modifiedInTrueBranch = gatherVariableUsageInfo(astNode.resultWhenTrue, referenceHashSetOf())
                    val modifiedInFalseBranch = gatherVariableUsageInfo(astNode.resultWhenFalse, referenceHashSetOf())

                    invalidatedVariables[astNode] = combineReferenceSets(modifiedInTrueBranch, modifiedInFalseBranch)
                    combineReferenceSets(modifiedUnderCurrentBase, modifiedInCondition, modifiedInTrueBranch, modifiedInFalseBranch)
                }
            }
        }

        gatherVariableUsageInfo(expression, referenceHashSetOf())

        // second stage is to actually produce CFG
        val cfgBuilder = ControlFlowGraphBuilder()
        var last = listOf<Pair<IFTNode, CFGLinkType>?>(null)
        var currentTemporaryRegisters = referenceHashMapOf<NamedNode, Register>()

        fun ControlFlowGraphBuilder.addNextCFG(nextCFG: ControlFlowGraph) {
            if (nextCFG.entryTreeRoot != null) {
                last.forEach { addLink(it, nextCFG.entryTreeRoot) }
                last = nextCFG.finalTreeRoots
            }
            addAllFrom(nextCFG)
        }

        fun ControlFlowGraphBuilder.addNextTree(nextTree: IntermediateFormTreeNode) {
            addNextCFG(ControlFlowGraphBuilder().apply { addLink(null, nextTree) }.build())
        }

        fun makeReadNode(readableNode: NamedNode): IntermediateFormTreeNode {
            // readableNode must be Variable or Function.Parameter
            val owner = variableProperties[readableNode]!!.owner
            return variableAccessGenerators[owner]!!.genRead(readableNode, owner === currentFunction)
        }

        fun makeCFGForSubtree(astNode: Expression): IntermediateFormTreeNode {
            return when (astNode) {
                is Expression.UnitLiteral -> IntermediateFormTreeNode.Const(IntermediateFormTreeNode.UNIT_VALUE)

                is Expression.BooleanLiteral -> IntermediateFormTreeNode.Const(if (astNode.value) 1 else 0)

                is Expression.NumberLiteral -> IntermediateFormTreeNode.Const(astNode.value)

                is Expression.Variable -> {
                    val readableNode = nameResolution[astNode]!!
                    if (astNode !in usagesThatRequireTempRegisters) {
                        makeReadNode(readableNode)
                    } else {
                        if (readableNode !in currentTemporaryRegisters) {
                            val valueNode = makeReadNode(readableNode)
                            val temporaryRegister = Register()
                            val assignmentNode = IntermediateFormTreeNode.RegisterWrite(temporaryRegister, valueNode)
                            currentTemporaryRegisters[readableNode] = temporaryRegister
                            cfgBuilder.addNextTree(assignmentNode)
                        }
                        IntermediateFormTreeNode.RegisterRead(currentTemporaryRegisters[readableNode]!!)
                    }
                }

                is Expression.UnaryOperation -> {
                    val subtreeNode = makeCFGForSubtree(astNode.operand)
                    when (astNode.kind) {
                        Expression.UnaryOperation.Kind.NOT -> IntermediateFormTreeNode.LogicalNegation(subtreeNode)
                        Expression.UnaryOperation.Kind.PLUS -> subtreeNode
                        Expression.UnaryOperation.Kind.MINUS -> IntermediateFormTreeNode.Negation(subtreeNode)
                        Expression.UnaryOperation.Kind.BIT_NOT -> IntermediateFormTreeNode.BitNegation(subtreeNode)
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
                        cfgBuilder.addNextTree(IntermediateFormTreeNode.RegisterWrite(resultTemporaryRegister, rightResultNode))
                        val lastAfterRight = last

                        last = mapLinkType(lastAfterLeft, shortCircuitLinkType)
                        val shortCircuitResultNode = IntermediateFormTreeNode.Const(shortCircuitValue)
                        cfgBuilder.addNextTree(IntermediateFormTreeNode.RegisterWrite(resultTemporaryRegister, shortCircuitResultNode))
                        val lastAfterShortCircuit = last

                        invalidatedVariables[astNode]!!.forEach { currentTemporaryRegisters.remove(it) }
                        last = lastAfterRight + lastAfterShortCircuit
                        IntermediateFormTreeNode.RegisterRead(resultTemporaryRegister)
                    }

                    else -> {
                        val leftSubtreeNode = makeCFGForSubtree(astNode.leftOperand)
                        val rightSubtreeNode = makeCFGForSubtree(astNode.rightOperand)
                        when (astNode.kind) {
                            Expression.BinaryOperation.Kind.AND,
                            Expression.BinaryOperation.Kind.OR -> throw Exception() // unreachable state
                            Expression.BinaryOperation.Kind.IFF -> IntermediateFormTreeNode.LogicalIff(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.XOR -> IntermediateFormTreeNode.LogicalXor(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.ADD -> IntermediateFormTreeNode.Add(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.SUBTRACT -> IntermediateFormTreeNode.Subtract(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.MULTIPLY -> IntermediateFormTreeNode.Multiply(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.DIVIDE -> IntermediateFormTreeNode.Divide(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.MODULO -> IntermediateFormTreeNode.Modulo(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.BIT_AND -> IntermediateFormTreeNode.BitAnd(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.BIT_OR -> IntermediateFormTreeNode.BitOr(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.BIT_XOR -> IntermediateFormTreeNode.BitXor(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.BIT_SHIFT_LEFT -> IntermediateFormTreeNode.BitShiftLeft(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.BIT_SHIFT_RIGHT -> IntermediateFormTreeNode.BitShiftRight(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.EQUALS -> IntermediateFormTreeNode.Equals(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.NOT_EQUALS -> IntermediateFormTreeNode.NotEquals(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.LESS_THAN -> IntermediateFormTreeNode.LessThan(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS -> IntermediateFormTreeNode.LessThanOrEquals(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.GREATER_THAN -> IntermediateFormTreeNode.GreaterThan(leftSubtreeNode, rightSubtreeNode)
                            Expression.BinaryOperation.Kind.GREATER_THAN_OR_EQUALS -> IntermediateFormTreeNode.GreaterThanOrEquals(leftSubtreeNode, rightSubtreeNode)
                        }
                    }
                }

                is Expression.FunctionCall -> {
                    val function = nameResolution[astNode] as Function
                    val parameterValues = Array<IntermediateFormTreeNode?>(function.parameters.size) { null }

                    val explicitArgumentsResultNodes = astNode.arguments.map { makeCFGForSubtree(it.value) }
                    for ((argument, resultNode) in astNode.arguments zip explicitArgumentsResultNodes) // explicit arguments
                        parameterValues[function.parameters.indexOfFirst { it === argumentResolution[argument] }] = resultNode
                    function.parameters.withIndex().filter { parameterValues[it.index] == null }.forEach { // default arguments
                        parameterValues[it.index] = makeReadNode(defaultParameterValues[it.value]!!)
                    }

                    val callIntermediateForm = functionDetailsGenerators[function]!!.genCall(parameterValues.map { it!! }.toList())
                    cfgBuilder.addNextCFG(callIntermediateForm.callGraph)
                    invalidatedVariables[astNode]!!.forEach { currentTemporaryRegisters.remove(it) }

                    if (callIntermediateForm.result != null) {
                        val temporaryResultRegister = Register()
                        cfgBuilder.addNextTree(IntermediateFormTreeNode.RegisterWrite(temporaryResultRegister, callIntermediateForm.result))
                        IntermediateFormTreeNode.RegisterRead(temporaryResultRegister)
                    } else {
                        IntermediateFormTreeNode.Const(IntermediateFormTreeNode.UNIT_VALUE)
                    }
                }

                is Expression.Conditional -> {
                    cfgBuilder.addNextTree(makeCFGForSubtree(astNode.condition))
                    val lastAfterCondition = last
                    val savedTemporaryRegisters = currentTemporaryRegisters.copy()
                    val resultTemporaryRegister = Register()

                    last = mapLinkType(lastAfterCondition, CFGLinkType.CONDITIONAL_TRUE)
                    val trueBranchResultNode = makeCFGForSubtree(astNode.resultWhenTrue)
                    cfgBuilder.addNextTree(IntermediateFormTreeNode.RegisterWrite(resultTemporaryRegister, trueBranchResultNode))
                    currentTemporaryRegisters = savedTemporaryRegisters
                    val lastAfterTrueBranch = last

                    last = mapLinkType(lastAfterCondition, CFGLinkType.CONDITIONAL_FALSE)
                    val falseBranchResultNode = makeCFGForSubtree(astNode.resultWhenFalse)
                    cfgBuilder.addNextTree(IntermediateFormTreeNode.RegisterWrite(resultTemporaryRegister, falseBranchResultNode))
                    val lastAfterFalseBranch = last

                    invalidatedVariables[astNode]!!.forEach { currentTemporaryRegisters.remove(it) }
                    last = lastAfterTrueBranch + lastAfterFalseBranch
                    IntermediateFormTreeNode.RegisterRead(resultTemporaryRegister)
                }
            }
        }

        val result = makeCFGForSubtree(expression)

        // build last tree into CFG, possibly wrapped in variable write operation
        if (targetVariable != null) {
            val owner = variableProperties[targetVariable]!!.owner
            cfgBuilder.addNextTree(variableAccessGenerators[owner]!!.genWrite(targetVariable, result, owner === currentFunction))
        } else {
            cfgBuilder.addNextTree(result)
        }

        return cfgBuilder.build()
    }

    fun createGraphForEachFunction(
        program: Program,
        createGraphForExpression: (Expression, Variable?, Function) -> ControlFlowGraph,
        nameResolution: ReferenceMap<Any, NamedNode>,
        defaultParameterValues: ReferenceMap<Function.Parameter, Variable>,
        diagnostics: Diagnostics
    ): Pair<ReferenceMap<Function, ControlFlowGraph>, ReferenceMap<Function, Variable>> {
        val controlFlowGraphs = referenceHashMapOf<Function, ControlFlowGraph>()
        val resultVariables = referenceHashMapOf<Function, Variable>() // only for non-Unit returning functions

        fun processFunction(function: Function) {
            val cfgBuilder = ControlFlowGraphBuilder()
            val variableToStoreResult: Variable? = if (function.returnType == Type.Unit) null else Variable(
                Variable.Kind.VALUE,
                "TODO",
                function.returnType,
                null
            )

            var last = listOf<Pair<IFTNode, CFGLinkType>?>(null)
            var breaking: MutableList<Pair<IFTNode, CFGLinkType>?>? = null
            var continuing: MutableList<Pair<IFTNode, CFGLinkType>?>? = null

            fun processStatementBlock(block: StatementBlock) {
                fun addExpression(expression: Expression, variable: Variable?): IFTNode? {
                    val cfg = createGraphForExpression(expression, variable, function)
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
                                    addExpression(parameter.defaultValue, defaultParameterValues[parameter])
                            }

                            processFunction(nestedFunction)
                        }

                        is Statement.Assignment -> addExpression(statement.value, nameResolution[statement] as Variable)

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

                        is Statement.Loop -> {
                            val conditionEntry = addExpression(statement.condition, null)!!
                            val conditionEnd = last

                            val outerBreaking = breaking
                            val outerContinuing = continuing

                            breaking = mutableListOf()
                            continuing = mutableListOf()

                            last = mapLinkType(conditionEnd, CFGLinkType.CONDITIONAL_TRUE)
                            processStatementBlock(statement.action)
                            val end = last

                            for (node in end + continuing!!)
                                cfgBuilder.addLink(node, conditionEntry)

                            last = mapLinkType(conditionEnd, CFGLinkType.CONDITIONAL_FALSE) + breaking!!

                            breaking = outerBreaking
                            continuing = outerContinuing
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
                            if (variableToStoreResult != null)
                                addExpression(statement.value, variableToStoreResult)
                            last = emptyList()
                        }
                    }
                }
            }

            processStatementBlock(function.body)

            controlFlowGraphs[function] = cfgBuilder.build()
            if (function.returnType != Type.Unit)
                resultVariables[function] = variableToStoreResult!!
        }

        program.globals.filterIsInstance<Program.Global.FunctionDefinition>().forEach { processFunction(it.function) }

        return Pair(controlFlowGraphs, resultVariables)
    }
}
