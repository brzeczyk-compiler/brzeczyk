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
import compiler.common.intermediate_form.FunctionDetailsGeneratorInterface
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.ReferenceSet
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
        functionDetailsGenerators: ReferenceMap<Function, FunctionDetailsGeneratorInterface>,
        argumentResolution: ArgumentResolutionResult
    ): ControlFlowGraph {
        return TODO()
    }

    fun createGraphForEachFunction(
        program: Program,
        createGraphForExpression: (Expression, Variable?) -> ControlFlowGraph,
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
                    val cfg = createGraphForExpression(expression, variable)

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
