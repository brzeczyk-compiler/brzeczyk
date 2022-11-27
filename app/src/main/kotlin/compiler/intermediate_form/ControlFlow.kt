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
import compiler.common.reference_collections.*
import compiler.semantic_analysis.ArgumentResolutionResult
import compiler.semantic_analysis.VariablePropertiesAnalyzer

object ControlFlow {
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
            return referenceSetOf(variableProperties.asSequence()
                .filter { (it.value.writtenIn intersect possiblyCalledFunctions).isNotEmpty() }
                .map { it.key as Variable }.toList())
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
                    }
                    else {
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
        var currentTemporaryRegisters = ReferenceHashMap<Variable, Register>()

        fun mergeCFGsUnconditionally(first: ControlFlowGraph?, second: ControlFlowGraph?): ControlFlowGraph? {
            return when {
                first == null -> second
                second == null -> first
                else -> {
                    val newLinks = referenceMapOf(first.finalTreeRoots.map { it to second.entryTreeRoot!! })
                    val unconditionalLinks = combineReferenceMaps(first.unconditionalLinks, second.unconditionalLinks, newLinks)
                    val conditionalTrueLinks = combineReferenceMaps(first.conditionalTrueLinks, second.conditionalTrueLinks)
                    val conditionalFalseLinks = combineReferenceMaps(first.conditionalFalseLinks, second.conditionalFalseLinks)
                    ControlFlowGraph(first.treeRoots + second.treeRoots, first.entryTreeRoot, unconditionalLinks, conditionalTrueLinks, conditionalFalseLinks)
                }
            }
        }

        fun mergeCFGsConditionally(condition: ControlFlowGraph, trueBranch: ControlFlowGraph, falseBranch: ControlFlowGraph): ControlFlowGraph {
            val treeRoots = condition.treeRoots + trueBranch.treeRoots + falseBranch.treeRoots
            val unconditionalLinks = combineReferenceMaps(condition.unconditionalLinks, trueBranch.unconditionalLinks, falseBranch.unconditionalLinks)
            val newTrueLinks = referenceMapOf(condition.finalTreeRoots.map { it to trueBranch.entryTreeRoot!! })
            val conditionalTrueLinks = combineReferenceMaps(condition.conditionalTrueLinks, trueBranch.conditionalTrueLinks, falseBranch.conditionalTrueLinks, newTrueLinks)
            val newFalseLinks = referenceMapOf(condition.finalTreeRoots.map { it to falseBranch.entryTreeRoot!! })
            val conditionalFalseLinks = combineReferenceMaps(condition.conditionalFalseLinks, trueBranch.conditionalFalseLinks, falseBranch.conditionalFalseLinks, newFalseLinks)
            return ControlFlowGraph(treeRoots, condition.entryTreeRoot, unconditionalLinks, conditionalTrueLinks, conditionalFalseLinks)
        }

        fun addTreeToCFG(cfg: ControlFlowGraph?, tree: IntermediateFormTreeNode): ControlFlowGraph {
            val singleTreeCFG = ControlFlowGraph(listOf(tree), tree, referenceMapOf(), referenceMapOf(), referenceMapOf())
            return mergeCFGsUnconditionally(cfg, singleTreeCFG)!!
        }

        fun makeVariableReadNode(variable: Variable): IntermediateFormTreeNode {
            val owner = variableProperties[variable]!!.owner!! // TODO: handle global variables
            return functionDetailsGenerators[owner]!!.genRead(variable, owner == currentFunction)
        }

        fun secondStageRecursion(astNode: Expression): Pair<ControlFlowGraph?, IntermediateFormTreeNode> {
            return when (astNode) {
                Expression.UnitLiteral ->
                    Pair(null, IntermediateFormTreeNode.Const(0))

                is Expression.BooleanLiteral ->
                    Pair(null, IntermediateFormTreeNode.Const(if (astNode.value) 1 else 0))

                is Expression.NumberLiteral ->
                    Pair(null, IntermediateFormTreeNode.Const(astNode.value.toLong()))

                is Expression.Variable -> {
                    val variable = nameResolution[astNode] as Variable
                    if (astNode !in usagesThatRequireTempRegisters) {
                        Pair(null, makeVariableReadNode(variable))
                    }
                    else{
                        var cfg: ControlFlowGraph? = null
                        if (variable !in currentTemporaryRegisters) {
                            val valueNode = makeVariableReadNode(variable)
                            val temporaryRegister = Register()
                            val assignmentNode = IntermediateFormTreeNode.RegisterWrite(temporaryRegister, valueNode)
                            currentTemporaryRegisters[variable] = temporaryRegister
                            cfg = ControlFlowGraph(listOf(assignmentNode), assignmentNode, referenceMapOf(), referenceMapOf(), referenceMapOf())
                        }
                        Pair(cfg, IntermediateFormTreeNode.RegisterRead(currentTemporaryRegisters.getValue(variable)))
                    }
                }

                is Expression.UnaryOperation -> {
                    val subCFG = secondStageRecursion(astNode.operand)
                    val newRootNode = when (astNode.kind) {
                        Expression.UnaryOperation.Kind.NOT -> IntermediateFormTreeNode.LogicalNegation(subCFG.second)
                        Expression.UnaryOperation.Kind.PLUS -> subCFG.second
                        Expression.UnaryOperation.Kind.MINUS -> IntermediateFormTreeNode.Negation(subCFG.second)
                        Expression.UnaryOperation.Kind.BIT_NOT -> IntermediateFormTreeNode.BitNegation(subCFG.second)
                    }
                    Pair(subCFG.first, newRootNode)
                }

                is Expression.BinaryOperation -> when (astNode.kind) {
                    Expression.BinaryOperation.Kind.AND, Expression.BinaryOperation.Kind.OR -> {
                        val logicalOr = astNode.kind == Expression.BinaryOperation.Kind.OR

                        val leftSubCFG = secondStageRecursion(astNode.leftOperand)
                        val rightSubCFG = secondStageRecursion(astNode.rightOperand)
                        invalidatedVariables[astNode]!!.forEach { currentTemporaryRegisters.remove(it) }

                        val temporaryRegister = Register()
                        val trueBranchAssignmentNode = IntermediateFormTreeNode.RegisterWrite(temporaryRegister,
                            if (logicalOr) IntermediateFormTreeNode.Const(1) else rightSubCFG.second)
                        val falseBranchAssignmentNode = IntermediateFormTreeNode.RegisterWrite(temporaryRegister,
                            if (logicalOr) rightSubCFG.second else IntermediateFormTreeNode.Const(0))

                        val combinedCFG = mergeCFGsConditionally(addTreeToCFG(leftSubCFG.first, leftSubCFG.second),
                            addTreeToCFG(if (logicalOr) null else rightSubCFG.first, trueBranchAssignmentNode),
                            addTreeToCFG(if (logicalOr) rightSubCFG.first else null, falseBranchAssignmentNode))
                        Pair(combinedCFG, IntermediateFormTreeNode.RegisterRead(temporaryRegister))
                    }

                    else -> {
                        val leftSubCFG = secondStageRecursion(astNode.leftOperand)
                        val rightSubCFG = secondStageRecursion(astNode.rightOperand)
                        val newRootNode = when (astNode.kind) {
                            Expression.BinaryOperation.Kind.AND,
                            Expression.BinaryOperation.Kind.OR -> throw Exception() // unreachable state
                            Expression.BinaryOperation.Kind.IFF -> IntermediateFormTreeNode.LogicalIff(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.XOR -> IntermediateFormTreeNode.LogicalXor(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.ADD -> IntermediateFormTreeNode.Add(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.SUBTRACT -> IntermediateFormTreeNode.Subtract(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.MULTIPLY -> IntermediateFormTreeNode.Multiply(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.DIVIDE -> IntermediateFormTreeNode.Divide(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.MODULO -> IntermediateFormTreeNode.Modulo(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.BIT_AND -> IntermediateFormTreeNode.BitAnd(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.BIT_OR -> IntermediateFormTreeNode.BitOr(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.BIT_XOR -> IntermediateFormTreeNode.BitXor(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.BIT_SHIFT_LEFT -> IntermediateFormTreeNode.BitShiftLeft(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.BIT_SHIFT_RIGHT -> IntermediateFormTreeNode.BitShiftRight(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.EQUALS -> IntermediateFormTreeNode.Equals(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.NOT_EQUALS -> IntermediateFormTreeNode.NotEquals(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.LESS_THAN -> IntermediateFormTreeNode.LessThan(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS -> IntermediateFormTreeNode.LessThanOrEquals(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.GREATER_THAN -> IntermediateFormTreeNode.GreaterThan(leftSubCFG.second, rightSubCFG.second)
                            Expression.BinaryOperation.Kind.GREATER_THAN_OR_EQUALS -> IntermediateFormTreeNode.GreaterThanOrEquals(leftSubCFG.second, rightSubCFG.second)
                        }
                        Pair(mergeCFGsUnconditionally(leftSubCFG.first, rightSubCFG.first), newRootNode)
                    }
                }

                is Expression.FunctionCall -> {
                    val function = nameResolution[astNode] as Function
                    val parameterValues = Array<IntermediateFormTreeNode?>(function.parameters.size) { null }

                    val argumentSubCFGs = astNode.arguments.map { secondStageRecursion(it.value) }
                    for ((argument, subCFG) in astNode.arguments zip argumentSubCFGs)
                        parameterValues[function.parameters.indexOf(argumentResolution[argument])] = subCFG.second // can use indexOf because parameter names are different
                    function.parameters.withIndex().filter { parameterValues[it.index] == null }.forEach() {
                        parameterValues[it.index] = makeVariableReadNode(defaultParameterValues[it.value]!!)
                    }

                    val callSubCFG = functionDetailsGenerators[function]!!.generateCall(parameterValues.map { it!! }.toList())
                    val combinedArgumentSubCFG = argumentSubCFGs.map { it.first }.reduceOrNull { acc, cfg -> mergeCFGsUnconditionally(acc, cfg) }
                    val combinedSubCFG = mergeCFGsUnconditionally(combinedArgumentSubCFG, callSubCFG.callGraph)
                    if (callSubCFG.result != null) {
                        val temporaryRegister = Register()
                        val resultAssignmentNode = IntermediateFormTreeNode.RegisterWrite(temporaryRegister, callSubCFG.result)
                        val resultReadNode = IntermediateFormTreeNode.RegisterRead(temporaryRegister)
                        Pair(addTreeToCFG(combinedSubCFG, resultAssignmentNode), resultReadNode)
                    }
                    else {
                        Pair(combinedSubCFG, IntermediateFormTreeNode.Const(0)) // unit placeholder value
                    }
                }

                is Expression.Conditional -> {
                    val conditionSubCFG = secondStageRecursion(astNode.condition)

                    val savedTemporaryRegisters = currentTemporaryRegisters.copy()
                    val trueBranchSubCFG = secondStageRecursion(astNode.resultWhenTrue)
                    currentTemporaryRegisters = savedTemporaryRegisters
                    val falseBranchSubCFG = secondStageRecursion(astNode.resultWhenFalse)
                    invalidatedVariables[astNode]!!.forEach { currentTemporaryRegisters.remove(it) }

                    val temporaryRegister = Register()
                    val trueBranchAssignmentNode = IntermediateFormTreeNode.RegisterWrite(temporaryRegister, trueBranchSubCFG.second)
                    val falseBranchAssignmentNode = IntermediateFormTreeNode.RegisterWrite(temporaryRegister, falseBranchSubCFG.second)
                    val combinedCFG = mergeCFGsConditionally(addTreeToCFG(conditionSubCFG.first, conditionSubCFG.second),
                        addTreeToCFG(trueBranchSubCFG.first, trueBranchAssignmentNode),
                        addTreeToCFG(falseBranchSubCFG.first, falseBranchAssignmentNode))
                    Pair(combinedCFG, IntermediateFormTreeNode.RegisterRead(temporaryRegister))
                }
            }
        }

        val result = secondStageRecursion(expression)
        return if (targetVariable == null)
            addTreeToCFG(result.first, result.second)
        else {
            val owner = variableProperties[targetVariable]!!.owner!! // TODO global variables
            val assignmentNode = functionDetailsGenerators[owner]!!.genWrite(targetVariable, result.second, owner == currentFunction)
            addTreeToCFG(result.first, assignmentNode)
        }
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
            val treeRoots = mutableListOf<IFTNode>()
            var entryTreeRoot: IFTNode? = null
            val unconditionalLinks = ReferenceHashMap<IFTNode, IFTNode>()
            val conditionalTrueLinks = ReferenceHashMap<IFTNode, IFTNode>()
            val conditionalFalseLinks = ReferenceHashMap<IFTNode, IFTNode>()

            fun link(from: Pair<IFTNode, LinkType>?, to: IFTNode) {
                if (from != null) {
                    val links = when (from.second) {
                        LinkType.UNCONDITIONAL -> unconditionalLinks
                        LinkType.CONDITIONAL_TRUE -> conditionalTrueLinks
                        LinkType.CONDITIONAL_FALSE -> conditionalFalseLinks
                    }

                    links[from.first] = to
                } else
                    entryTreeRoot = to
            }

            fun mapLinkType(list: List<Pair<IFTNode, LinkType>?>, type: LinkType) = list.map { it?.copy(second = type) }

            var last = listOf<Pair<IFTNode, LinkType>?>(null)
            var breaking: MutableList<Pair<IFTNode, LinkType>?>? = null
            var continuing: MutableList<Pair<IFTNode, LinkType>?>? = null

            fun processStatementBlock(block: StatementBlock) {
                fun addExpression(expression: Expression, variable: Variable?): IFTNode? {
                    val cfg = createGraphForExpression(expression, variable, function)

                    treeRoots.addAll(cfg.treeRoots)
                    unconditionalLinks.putAll(cfg.unconditionalLinks)
                    conditionalTrueLinks.putAll(cfg.conditionalTrueLinks)
                    conditionalFalseLinks.putAll(cfg.conditionalFalseLinks)

                    val entry = cfg.entryTreeRoot

                    if (entry != null) {
                        for (node in last)
                            link(node, entry)

                        last = cfg.finalTreeRoots.map { Pair(it, LinkType.UNCONDITIONAL) }
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

                            last = mapLinkType(conditionEnd, LinkType.CONDITIONAL_TRUE)
                            processStatementBlock(statement.actionWhenTrue)
                            val trueBranchEnd = last

                            last = mapLinkType(conditionEnd, LinkType.CONDITIONAL_FALSE)
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

                            last = mapLinkType(conditionEnd, LinkType.CONDITIONAL_TRUE)
                            processStatementBlock(statement.action)
                            val end = last

                            for (node in end + continuing!!)
                                link(node, conditionEntry)

                            last = mapLinkType(conditionEnd, LinkType.CONDITIONAL_FALSE) + breaking!!

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

            controlFlowGraphs[function] = ControlFlowGraph(
                treeRoots,
                entryTreeRoot,
                unconditionalLinks,
                conditionalTrueLinks,
                conditionalFalseLinks
            )
        }

        program.globals.filterIsInstance<Program.Global.FunctionDefinition>().forEach { processFunction(it.function) }

        return controlFlowGraphs
    }

    private enum class LinkType {
        UNCONDITIONAL,
        CONDITIONAL_TRUE,
        CONDITIONAL_FALSE
    }
}
