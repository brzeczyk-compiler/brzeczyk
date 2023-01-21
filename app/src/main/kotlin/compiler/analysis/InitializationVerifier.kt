package compiler.analysis

import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Expression.BinaryOperation.Kind.AND
import compiler.ast.Expression.BinaryOperation.Kind.OR
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Program.Global
import compiler.ast.Statement
import compiler.ast.Variable
import compiler.diagnostics.Diagnostic.ResolutionDiagnostic.VariableInitializationError.ReferenceToUninitializedVariable
import compiler.diagnostics.Diagnostics
import compiler.utils.Ref
import compiler.utils.mutableRefSetOf

object InitializationVerifier {
    data class VerificationState(
        val initializedVariables: MutableSet<Ref<NamedNode>> = mutableRefSetOf(),
        val traversedFunctions: MutableSet<Ref<Function>> = mutableRefSetOf()
    ) {
        fun copy(): VerificationState = VerificationState(initializedVariables.toMutableSet(), traversedFunctions.toMutableSet())
        fun add(other: VerificationState) {
            initializedVariables.addAll(other.initializedVariables)
            traversedFunctions.addAll(other.traversedFunctions)
        }
        fun restore(state: VerificationState) {
            initializedVariables.clear()
            initializedVariables.addAll(state.initializedVariables)
            traversedFunctions.clear()
            traversedFunctions.addAll(state.traversedFunctions)
        }
        infix fun intersect(other: VerificationState) = VerificationState(
            (initializedVariables intersect other.initializedVariables).toMutableSet(),
            (traversedFunctions intersect other.traversedFunctions).toMutableSet()
        )
    }

    fun verifyAccessedVariablesAreInitialized(
        ast: Program,
        nameResolution: Map<Ref<AstNode>, Ref<NamedNode>>,
        defaultParameterMapping: Map<Ref<Function.Parameter>, Variable>,
        diagnostics: Diagnostics
    ) {
        fun verifyInitialization(
            node: AstNode,
            state: VerificationState
        ): VerificationState? {
            // Consider a function f, which performs g(). g might contain conditional returns.
            // From f's point of view, the only variables which will for sure get initialized in g
            // are the ones which will be initialized before first *potentially* returning instruction
            // However, to verify correctness of g we need to check what happens if the conditional returns do not happen
            // That's why when traversing g() we have to analyze all instructions, but in f we want to use state from g()
            // which was the last possible state before first potentially returning instruction.
            // The returned value of this function indicates the state we reach before first potentially returning instruction in
            // the Ast subtree that we are now verifying. If it is null, it means there were no potential top-level returns.

            fun handleInstructionBlock(
                statements: List<AstNode>,
                state: VerificationState
            ): VerificationState? {
                var stateBeforeFirstReturn: VerificationState? = null
                statements.forEach { statement ->
                    verifyInitialization(statement, state)?.let { stateBeforeFirstReturn = stateBeforeFirstReturn ?: it }
                }
                return stateBeforeFirstReturn
            }

            fun handleLoopBody(body: List<AstNode>): VerificationState? {
                // loop might never execute
                // even if we made some progress until first return we want to discard it,
                // but we still need to notify of potential return
                return if (handleInstructionBlock(body, state.copy()) != null) state.copy() else null
            }

            fun handleConditional(
                condition: AstNode,
                actionsWhenTrue: List<AstNode>,
                actionsWhenFalse: List<AstNode>,
                state: VerificationState
            ): VerificationState? {
                // no top-level returns in condition
                verifyInitialization(condition, state)
                val stateAfterTrue = state.copy()
                val stateBeforeFirstReturnInTrue = handleInstructionBlock(actionsWhenTrue, stateAfterTrue)
                val stateAfterFalse = state.copy()
                val stateBeforeFirstReturnInFalse = handleInstructionBlock(actionsWhenFalse, stateAfterFalse)
                state.add(stateAfterTrue intersect stateAfterFalse)
                if (stateBeforeFirstReturnInTrue != null && stateBeforeFirstReturnInFalse != null) {
                    return stateBeforeFirstReturnInTrue intersect stateBeforeFirstReturnInFalse
                }
                // even when only one side is potentially returning it makes the whole conditional potentially returning
                if (stateBeforeFirstReturnInTrue != null) return stateBeforeFirstReturnInTrue intersect stateAfterFalse
                if (stateBeforeFirstReturnInFalse != null) return stateBeforeFirstReturnInFalse intersect stateAfterTrue
                return null
            }

            return when (node) {
                is Statement.Evaluation -> verifyInitialization(node.expression, state)
                is Statement.VariableDefinition -> verifyInitialization(node.variable, state)
                is Global.VariableDefinition -> verifyInitialization(node.variable, state)
                is Statement.FunctionDefinition -> verifyInitialization(node.function, state)
                is Global.FunctionDefinition -> verifyInitialization(node.function, state)
                is Statement.Assignment -> {
                    verifyInitialization(node.value, state)
                    if (node.lvalue is Statement.Assignment.LValue.Variable) {
                        // the variable will be initialized after assignment, not before
                        state.initializedVariables.add(nameResolution[Ref(node)]!!)
                    }
                    // assignment to one field in array will not affect it's initalization state
                    null
                }
                is Statement.Block -> handleInstructionBlock(node.block, state)
                is Statement.Conditional -> {
                    handleConditional(
                        node.condition,
                        node.actionWhenTrue, node.actionWhenFalse ?: listOf(), state
                    )
                }
                is Statement.Loop -> {
                    verifyInitialization(node.condition, state)
                    handleLoopBody(node.action)
                }
                is Statement.ForeachLoop -> {
                    verifyInitialization(node.generatorCall, state)
                    // this variable should be very basic, so there is no need to traverse it
                    state.initializedVariables.add(Ref(node.receivingVariable))
                    handleLoopBody(node.action)
                }
                is Statement.FunctionReturn -> {
                    // the return value itself cannot contain a top-level return
                    verifyInitialization(node.value, state)
                    return state.copy()
                }
                is Statement.GeneratorYield -> {
                    // yield itself will not contain a top-level return
                    verifyInitialization(node.value, state)
                    // we might never come back if the calling for each loop breaks
                    return state.copy()
                }
                is Expression.Variable -> {
                    val resolvedVariable: NamedNode = nameResolution[Ref(node)]!!.value
                    if (Ref(resolvedVariable) !in state.initializedVariables) {
                        diagnostics.report(ReferenceToUninitializedVariable(resolvedVariable))
                    }
                    null
                }
                is Expression.FunctionCall -> {
                    // arguments will not contain top-level returns
                    node.arguments.forEach { verifyInitialization(it, state) }
                    val resolvedFunction: Function = nameResolution[Ref(node)]!!.value as Function
                    var stateBeforeFirstReturn: VerificationState? = null
                    if (Ref(resolvedFunction) !in state.traversedFunctions) {
                        state.traversedFunctions.add(Ref(resolvedFunction))
                        stateBeforeFirstReturn = handleInstructionBlock(resolvedFunction.body, state)
                    }
                    stateBeforeFirstReturn?.let { state.restore(it) }
                    null
                }
                is Expression.FunctionCall.Argument -> verifyInitialization(node.value, state)
                is Expression.UnaryOperation -> verifyInitialization(node.operand, state)
                is Expression.BinaryOperation -> {
                    verifyInitialization(node.leftOperand, state)
                    when (node.kind) {
                        in listOf(AND, OR) -> verifyInitialization(node.rightOperand, state.copy())
                        else -> verifyInitialization(node.rightOperand, state)
                    }
                    // no top-level return in binary operation
                    null
                }
                is Expression.Conditional -> handleConditional(
                    node.condition, listOf(node.resultWhenTrue), listOf(node.resultWhenFalse),
                    state
                )
                is Function -> {
                    node.parameters.forEach { verifyInitialization(it, state) }
                    // no top-level return in parameters
                    null
                }
                is Function.Parameter -> {
                    state.initializedVariables.add(Ref(node))
                    defaultParameterMapping[Ref(node)]?.let { state.initializedVariables.add(Ref(it)) }
                    node.defaultValue?.let { verifyInitialization(it, state) }
                    null
                }
                is Variable -> {
                    if (node.value != null) {
                        verifyInitialization(node.value, state)
                        state.initializedVariables.add(Ref(node))
                    }
                    null
                }
                is Expression.ArrayElement -> {
                    verifyInitialization(node.expression, state)
                    verifyInitialization(node.index, state)
                }
                is Expression.ArrayLength -> verifyInitialization(node.expression, state)
                is Expression.ArrayAllocation -> {
                    verifyInitialization(node.size, state)
                    node.initialization.forEach { verifyInitialization(it, state) }
                    // no top-level returns in initialization values
                    null
                }
                is Expression.ArrayGeneration -> verifyInitialization(node.generatorCall, state)
                else -> null
            }
        }
        val state = VerificationState()
        ast.globals.forEach { verifyInitialization(it, state) }
        // we now need to simulate the execution of top level functions to verify their correctness
        ast.globals.filterIsInstance<Global.FunctionDefinition>().forEach {
            val stateLocal = state.copy()
            // don't traverse this function, we're doing this now
            stateLocal.traversedFunctions.add(Ref(it.function))
            // don't care about returns, there is no parent function in global context
            it.function.body.forEach { instruction -> verifyInitialization(instruction, stateLocal) }
        }
    }
}
