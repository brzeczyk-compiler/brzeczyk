package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.diagnostics.Diagnostic.ControlFlowDiagnostic
import compiler.common.diagnostics.Diagnostics
import compiler.common.reference_collections.ReferenceHashMap
import compiler.common.reference_collections.ReferenceMap

object ControlFlow {
    fun createGraphForExpression(expression: Expression, variable: Variable?): ControlFlowGraph {
        return TODO()
    }

    fun createGraphForEachFunction(
        program: Program,
        createGraphForExpression: (Expression, Variable?) -> ControlFlowGraph,
        nameResolution: ReferenceMap<Any, NamedNode>,
        defaultParameterValues: ReferenceMap<Function.Parameter, Variable>,
        diagnostics: Diagnostics
    ): Pair<ReferenceMap<Function, ControlFlowGraph>, ReferenceMap<Function, Variable>> {
        val controlFlowGraphs = ReferenceHashMap<Function, ControlFlowGraph>()
        val resultVariables = ReferenceHashMap<Function, Variable>() // only for non-Unit returning functions

        fun processFunction(function: Function) {
            val cfgBuilder = ControlFlowGraphBuilder()
            var variableToStoreResult: Variable? = null

            fun mapLinkType(list: List<Pair<IFTNode, CFGLinkType>?>, type: CFGLinkType) = list.map { it?.copy(second = type) }

            var last = listOf<Pair<IFTNode, CFGLinkType>?>(null)
            var breaking: MutableList<Pair<IFTNode, CFGLinkType>?>? = null
            var continuing: MutableList<Pair<IFTNode, CFGLinkType>?>? = null

            fun processStatementBlock(block: StatementBlock) {
                fun addExpression(expression: Expression, variable: Variable?): IFTNode? {
                    val cfg = createGraphForExpression(expression, variable)
                    val entry = cfg.entryTreeRoot

                    if (entry != null) {
                        for (node in last) {
                            cfgBuilder.addLink(node, entry)
                        }

                        last = cfg.finalTreeRoots.map { Pair(it, CFGLinkType.UNCONDITIONAL) }
                    }
                    cfgBuilder.addAllFrom(cfg)

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
                            if (function.returnType != Type.Unit)
                                variableToStoreResult = Variable(
                                    Variable.Kind.VALUE,
                                    "TODO",
                                    function.returnType,
                                    null
                                )

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
