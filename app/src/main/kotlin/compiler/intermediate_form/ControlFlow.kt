package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostic.ControlFlowDiagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.common.reference_collections.MutableReferenceSet
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceHashSet
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.ReferenceSet
import compiler.common.reference_collections.combineReferenceSets
import compiler.common.reference_collections.copy
import compiler.common.reference_collections.referenceSetOf
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
        defaultParameterValues: ReferenceMap<Function.Parameter, Variable>
    ): ControlFlowGraph {
        fun getVariablesModifiedBy(function: Function): ReferenceSet<Variable> {
            val possiblyCalledFunctions = combineReferenceSets(callGraph[function]!!, referenceSetOf(function))
            return referenceSetOf(
                variableProperties.asSequence()
                    .filter { (it.value.writtenIn intersect possiblyCalledFunctions).isNotEmpty() }
                    .map { it.key as Variable }.toList()
            )
        }

        // first stage is to decide which variable usages have to be realized via temporary registers
        // and which variables are invalidated by function calls / conditionals
        val usagesThatRequireTempRegisters = ReferenceHashSet<Expression.Variable>()
        val invalidatedVariables = ReferenceHashMap<Expression, ReferenceSet<Variable>>()

        fun firstStageRecursion(astNode: Expression, modifiedUnderCurrentBase: MutableReferenceSet<Variable>) {
            when (astNode) {
                Expression.UnitLiteral,
                is Expression.BooleanLiteral,
                is Expression.NumberLiteral -> {}

                is Expression.Variable ->
                    if (nameResolution[astNode] in modifiedUnderCurrentBase)
                        usagesThatRequireTempRegisters.add(astNode)

                is Expression.UnaryOperation ->
                    firstStageRecursion(astNode.operand, modifiedUnderCurrentBase)

                is Expression.BinaryOperation -> {
                    if (astNode.kind in listOf(Expression.BinaryOperation.Kind.OR, Expression.BinaryOperation.Kind.AND)) {
                        val leftModifiedVariables = ReferenceHashSet<Variable>()
                        firstStageRecursion(astNode.leftOperand, leftModifiedVariables)

                        val rightModifiedVariables = ReferenceHashSet<Variable>()
                        firstStageRecursion(astNode.rightOperand, rightModifiedVariables)

                        modifiedUnderCurrentBase.addAll(leftModifiedVariables)
                        modifiedUnderCurrentBase.addAll(rightModifiedVariables)
                        invalidatedVariables[astNode] = rightModifiedVariables
                    } else {
                        firstStageRecursion(astNode.rightOperand, modifiedUnderCurrentBase)
                        firstStageRecursion(astNode.leftOperand, modifiedUnderCurrentBase)
                    }
                }

                is Expression.FunctionCall -> {
                    val variablesModifiedInArguments = ReferenceHashSet<Variable>()
                    for (argumentNode in astNode.arguments) {
                        val modifiedVariables = ReferenceHashSet<Variable>()
                        firstStageRecursion(argumentNode.value, modifiedVariables)
                        variablesModifiedInArguments.addAll(modifiedVariables)
                    }

                    val variablesModifiedByCall = getVariablesModifiedBy(nameResolution[astNode] as Function)
                    invalidatedVariables[astNode] = variablesModifiedByCall
                    modifiedUnderCurrentBase.addAll(variablesModifiedByCall)
                    modifiedUnderCurrentBase.addAll(variablesModifiedInArguments)
                }

                is Expression.Conditional -> {
                    val conditionModifiedVariables = ReferenceHashSet<Variable>()
                    firstStageRecursion(astNode.condition, conditionModifiedVariables)

                    val trueBranchModifiedVariables = ReferenceHashSet<Variable>()
                    firstStageRecursion(astNode.resultWhenTrue, trueBranchModifiedVariables)

                    val falseBranchModifiedVariables = ReferenceHashSet<Variable>()
                    firstStageRecursion(astNode.resultWhenFalse, falseBranchModifiedVariables)

                    modifiedUnderCurrentBase.addAll(conditionModifiedVariables)
                    modifiedUnderCurrentBase.addAll(trueBranchModifiedVariables)
                    modifiedUnderCurrentBase.addAll(falseBranchModifiedVariables)

                    invalidatedVariables[astNode] = combineReferenceSets(trueBranchModifiedVariables, falseBranchModifiedVariables)
                }
            }
        }

        firstStageRecursion(expression, ReferenceHashSet())

        // second stage is to actually produce CFG
        val cfgBuilder = ControlFlowGraphBuilder()
        var last = listOf<Pair<IFTNode, CFGLinkType>?>(null)
        var currentTemporaryRegisters = ReferenceHashMap<Variable, Register>()

        fun ControlFlowGraphBuilder.addNextCFG(nextCFG: ControlFlowGraph) {
            addAllFrom(nextCFG)
            if (nextCFG.entryTreeRoot != null) {
                last.forEach { addLink(it, nextCFG.entryTreeRoot) }
                last = nextCFG.finalTreeRoots.map { Pair(it, CFGLinkType.UNCONDITIONAL) }
            }
        }

        fun ControlFlowGraphBuilder.addNextTree(nextTree: IntermediateFormTreeNode) {
            addNextCFG(ControlFlowGraphBuilder().apply { addLink(null, nextTree) }.build())
        }

        fun makeVariableReadNode(variable: Variable): IntermediateFormTreeNode {
            val owner = variableProperties[variable]!!.owner!! // TODO: handle global variables
            return functionDetailsGenerators[owner]!!.genRead(variable, owner == currentFunction)
        }

        fun secondStageRecursion(astNode: Expression): IntermediateFormTreeNode {
            return when (astNode) {
                Expression.UnitLiteral -> IntermediateFormTreeNode.Const(IntermediateFormTreeNode.UNIT_VALUE)

                is Expression.BooleanLiteral -> IntermediateFormTreeNode.Const(if (astNode.value) 1 else 0)

                is Expression.NumberLiteral -> IntermediateFormTreeNode.Const(astNode.value.toLong())

                is Expression.Variable -> {
                    val variable = nameResolution[astNode] as Variable
                    if (astNode !in usagesThatRequireTempRegisters) {
                        makeVariableReadNode(variable)
                    } else {
                        if (variable !in currentTemporaryRegisters) {
                            val valueNode = makeVariableReadNode(variable)
                            val temporaryRegister = Register()
                            val assignmentNode = IntermediateFormTreeNode.RegisterWrite(temporaryRegister, valueNode)
                            currentTemporaryRegisters[variable] = temporaryRegister
                            cfgBuilder.addNextTree(assignmentNode)
                        }
                        IntermediateFormTreeNode.RegisterRead(currentTemporaryRegisters.getValue(variable))
                    }
                }

                is Expression.UnaryOperation -> {
                    val subtreeNode = secondStageRecursion(astNode.operand)
                    when (astNode.kind) {
                        Expression.UnaryOperation.Kind.NOT -> IntermediateFormTreeNode.LogicalNegation(subtreeNode)
                        Expression.UnaryOperation.Kind.PLUS -> subtreeNode
                        Expression.UnaryOperation.Kind.MINUS -> IntermediateFormTreeNode.Negation(subtreeNode)
                        Expression.UnaryOperation.Kind.BIT_NOT -> IntermediateFormTreeNode.BitNegation(subtreeNode)
                    }
                }

                is Expression.BinaryOperation -> when (astNode.kind) {
                    Expression.BinaryOperation.Kind.AND, Expression.BinaryOperation.Kind.OR -> {
                        val isLogicalOr = astNode.kind == Expression.BinaryOperation.Kind.OR

                        cfgBuilder.addNextTree(secondStageRecursion(astNode.leftOperand))
                        val lastAfterLeft = last
                        val resultTemporaryRegister = Register()

                        last = mapLinkType(lastAfterLeft, if (isLogicalOr) CFGLinkType.CONDITIONAL_FALSE else CFGLinkType.CONDITIONAL_TRUE)
                        val rightResultNode = secondStageRecursion(astNode.rightOperand)
                        cfgBuilder.addNextTree(IntermediateFormTreeNode.RegisterWrite(resultTemporaryRegister, rightResultNode))
                        val lastAfterRight = last

                        last = mapLinkType(lastAfterLeft, if (isLogicalOr) CFGLinkType.CONDITIONAL_TRUE else CFGLinkType.CONDITIONAL_FALSE)
                        val shortCircuitResultNode = IntermediateFormTreeNode.Const(if (isLogicalOr) 1 else 0)
                        cfgBuilder.addNextTree(IntermediateFormTreeNode.RegisterWrite(resultTemporaryRegister, shortCircuitResultNode))
                        val lastAfterShortCircuit = last

                        invalidatedVariables[astNode]!!.forEach { currentTemporaryRegisters.remove(it) }
                        last = lastAfterRight + lastAfterShortCircuit
                        IntermediateFormTreeNode.RegisterRead(resultTemporaryRegister)
                    }

                    else -> {
                        val leftSubtreeNode = secondStageRecursion(astNode.leftOperand)
                        val rightSubtreeNode = secondStageRecursion(astNode.rightOperand)
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

                    val explicitArgumentsResultNodes = astNode.arguments.map { secondStageRecursion(it.value) }
                    for ((argument, resultNode) in astNode.arguments zip explicitArgumentsResultNodes) // explicit arguments
                        parameterValues[function.parameters.indexOfFirst { it === argumentResolution[argument] }] = resultNode
                    function.parameters.withIndex().filter { parameterValues[it.index] == null }.forEach() { // default arguments
                        parameterValues[it.index] = makeVariableReadNode(defaultParameterValues[it.value]!!)
                    }

                    val callIntermediateForm = functionDetailsGenerators[function]!!.generateCall(parameterValues.map { it!! }.toList())
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
                    cfgBuilder.addNextTree(secondStageRecursion(astNode.condition))
                    val lastAfterCondition = last
                    val savedTemporaryRegisters = currentTemporaryRegisters.copy()
                    val resultTemporaryRegister = Register()

                    last = mapLinkType(lastAfterCondition, CFGLinkType.CONDITIONAL_TRUE)
                    val trueBranchResultNode = secondStageRecursion(astNode.resultWhenTrue)
                    cfgBuilder.addNextTree(IntermediateFormTreeNode.RegisterWrite(resultTemporaryRegister, trueBranchResultNode))
                    currentTemporaryRegisters = savedTemporaryRegisters
                    val lastAfterTrueBranch = last

                    last = mapLinkType(lastAfterCondition, CFGLinkType.CONDITIONAL_FALSE)
                    val falseBranchResultNode = secondStageRecursion(astNode.resultWhenFalse)
                    cfgBuilder.addNextTree(IntermediateFormTreeNode.RegisterWrite(resultTemporaryRegister, falseBranchResultNode))
                    val lastAfterFalseBranch = last

                    invalidatedVariables[astNode]!!.forEach { currentTemporaryRegisters.remove(it) }
                    last = lastAfterTrueBranch + lastAfterFalseBranch
                    IntermediateFormTreeNode.RegisterRead(resultTemporaryRegister)
                }
            }
        }

        val result = secondStageRecursion(expression)
        if (targetVariable != null) {
            val owner = variableProperties[targetVariable]!!.owner!! // TODO: handle global variables
            cfgBuilder.addNextTree(functionDetailsGenerators[owner]!!.genWrite(targetVariable, result, owner == currentFunction))
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
    ): ReferenceMap<Function, ControlFlowGraph> {
        val controlFlowGraphs = ReferenceHashMap<Function, ControlFlowGraph>()

        fun processFunction(function: Function) {
            val cfgBuilder = ControlFlowGraphBuilder()

            var last = listOf<Pair<IFTNode, CFGLinkType>?>(null)
            var breaking: MutableList<Pair<IFTNode, CFGLinkType>?>? = null
            var continuing: MutableList<Pair<IFTNode, CFGLinkType>?>? = null

            fun processStatementBlock(block: StatementBlock) {
                fun addExpression(expression: Expression, variable: Variable?): IFTNode? {
                    val cfg = createGraphForExpression(expression, variable, function)
                    cfgBuilder.addAllFrom(cfg)

                    val entry = cfg.entryTreeRoot

                    if (entry != null) {
                        for (node in last)
                            cfgBuilder.addLink(node, entry)

                        last = cfg.finalTreeRoots.map { Pair(it, CFGLinkType.UNCONDITIONAL) }
                    }

                    return entry
                }

                for (statement in block) {
                    if (last.isEmpty())
                        diagnostics.report(ControlFlowDiagnostic.UnreachableStatement(statement))

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
                                diagnostics.report(ControlFlowDiagnostic.BreakOutsideOfLoop(statement))

                            last = emptyList()
                        }

                        is Statement.LoopContinuation -> {
                            if (continuing != null)
                                continuing!!.addAll(last)
                            else
                                diagnostics.report(ControlFlowDiagnostic.ContinuationOutsideOfLoop(statement))

                            last = emptyList()
                        }

                        is Statement.FunctionReturn -> {
                            addExpression(statement.value, null)

                            last = emptyList()
                        }
                    }
                }
            }

            processStatementBlock(function.body)

            controlFlowGraphs[function] = cfgBuilder.build()
        }

        program.globals.filterIsInstance<Program.Global.FunctionDefinition>().forEach { processFunction(it.function) }

        return controlFlowGraphs
    }
}
