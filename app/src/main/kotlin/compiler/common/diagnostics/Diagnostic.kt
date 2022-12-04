package compiler.common.diagnostics

import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.lexer.Location
import compiler.lexer.LocationRange

sealed interface Diagnostic {
    fun isError(): Boolean

    data class LexerError(
        val start: Location,
        val end: Location?,
        val context: List<String>,
        val errorSegment: String,
    ) : Diagnostic {
        override fun isError() = true

        override fun toString() = StringBuilder()
            .append("Lexer Error\n")
            .append("The token at location $start - ${end ?: "eof"} is unexpected.\n")
            .append("Context: \t\t${context.joinToString("")}-->$errorSegment<---\n")
            .toString()
    }

    sealed class ParserError : Diagnostic {
        override fun isError() = true

        abstract val errorMessage: String

        override fun toString(): String = "Parser Error\n${errorMessage}\n"

        class UnexpectedToken(
            symbol: Any?,
            location: LocationRange,
        ) : ParserError() {
            override val errorMessage =
                if (symbol != null) "The symbol <<$symbol>> is unexpected at location $location."
                else "The end of file is unexpected."
        }

        class InvalidNumberLiteral(
            number: String,
            location: LocationRange,
        ) : ParserError() {
            override val errorMessage = "The literal <<$number>> at location $location is an invalid number literal."
        }
    }

    sealed class ResolutionError(astNodes: List<AstNode>) : Diagnostic {

        data class ObjectAssociatedToError(val message: String, val location: LocationRange?)

        val associatedObjects = astNodes.map { ObjectAssociatedToError(it.print(), it.location) }

        abstract val errorMessage: String

        override fun toString(): String = StringBuilder().apply {
            if (isError()) append("Resolver Error") else append("Resolver Warning")
            append("\n")
            associatedObjects.forEach { append(it.message).append("\n") }
            append(errorMessage).append("\n")
        }.toString()

        sealed class NameResolutionError(astNodes: List<AstNode>) : ResolutionError(astNodes) {
            override fun isError() = true

            class UndefinedVariable(
                variable: Expression.Variable,
            ) : NameResolutionError(listOf(variable)) {
                override val errorMessage = "The variable is undefined."
            }

            class AssignmentToUndefinedVariable(
                assignment: Statement.Assignment,
            ) : NameResolutionError(listOf(assignment)) {
                override val errorMessage = "A variable to be assigned to is undefined."
            }

            class UndefinedFunction(
                functionCall: Expression.FunctionCall,
            ) : NameResolutionError(listOf(functionCall)) {
                override val errorMessage = "The called function is undefined."
            }

            class NameConflict(
                vararg nodesWithSameName: NamedNode,
            ) : NameResolutionError(nodesWithSameName.asList()) {
                override val errorMessage = "There is a naming conflict."
            }

            class VariableIsNotCallable(
                variableOrParameter: NamedNode,
                functionCall: Expression.FunctionCall,
            ) : NameResolutionError(listOf(variableOrParameter, functionCall)) {
                override val errorMessage = "The variable is called."
            }

            class FunctionIsNotVariable(
                function: Function,
                variable: Expression.Variable,
            ) : NameResolutionError(listOf(function, variable)) {
                override val errorMessage = "The function is used as a variable."
            }

            class AssignmentToFunction(
                function: Function,
                assignment: Statement.Assignment,
            ) : NameResolutionError(listOf(function, assignment)) {
                override val errorMessage = "The function is a left operand of the assignment."
            }
        }

        sealed class ArgumentResolutionError(astNodes: List<AstNode>) : ResolutionError(astNodes) {
            override fun isError(): Boolean = true

            class DefaultParametersNotLast(
                function: Function,
            ) : ArgumentResolutionError(listOf(function)) {
                override val errorMessage = "An argument with a default value is listed before an argument without a default value."
            }

            class PositionalArgumentAfterNamed(
                functionCall: Expression.FunctionCall,
            ) : ArgumentResolutionError(listOf(functionCall)) {
                override val errorMessage = "A named argument is listed before a positional argument in the function call."
            }

            class MissingArgument(
                function: Function,
                functionCall: Expression.FunctionCall,
                parameter: Function.Parameter,
            ) : ArgumentResolutionError(listOf(function, functionCall, parameter)) {
                override val errorMessage = "An argument for the parameter of the function is missing in the call."
            }

            class RepeatedArgument(
                function: Function,
                functionCall: Expression.FunctionCall,
                parameter: Function.Parameter,
            ) : ArgumentResolutionError(listOf(function, functionCall, parameter)) {
                override val errorMessage = "Multiple arguments are provided for the parameter of the function in the call."
            }

            class UnknownArgument(
                function: Function,
                functionCall: Expression.FunctionCall,
                argument: Expression.FunctionCall.Argument,
            ) : ArgumentResolutionError(listOf(function, functionCall, argument)) {
                override val errorMessage = "The function does not have such named parameter."
            }

            class TooManyArguments(
                functionCall: Expression.FunctionCall,
            ) : ArgumentResolutionError(listOf(functionCall)) {
                override val errorMessage = "Too many arguments are provided in the function call."
            }
        }

        sealed class TypeCheckingError(astNodes: List<AstNode>) : ResolutionError(astNodes) {
            override fun isError() = true

            class ConstantWithoutValue(
                variable: Variable,
            ) : TypeCheckingError(listOf(variable)) {
                override val errorMessage = "The constant does not have a value."
            }

            class UninitializedGlobalVariable(
                variable: Variable,
            ) : TypeCheckingError(listOf(variable)) {
                override val errorMessage = "The global variable is not initialized."
            }

            class ImmutableAssignment(
                assignment: Statement.Assignment,
                variable: Variable,
            ) : TypeCheckingError(listOf(assignment, variable)) {
                override val errorMessage = "The assignment assigns to the ${if (variable.kind == Variable.Kind.CONSTANT) "constant" else "value"}."
            }

            class ParameterAssignment(
                assignment: Statement.Assignment,
                parameter: Function.Parameter,
            ) : TypeCheckingError(listOf(assignment, parameter)) {
                override val errorMessage = "The assignment assigns to the parameter."
            }

            class FunctionAssignment(
                assignment: Statement.Assignment,
                function: Function,
            ) : TypeCheckingError(listOf(assignment, function)) {
                override val errorMessage = "The assignment assigns to the function."
            }

            class FunctionAsValue(
                expression: Expression.Variable,
                function: Function,
            ) : TypeCheckingError(listOf(expression, function)) {
                override val errorMessage = "The function is used as a value."
            }

            class VariableCall(
                functionCall: Expression.FunctionCall,
                variable: Variable,
            ) : TypeCheckingError(listOf(functionCall, variable)) {
                override val errorMessage = "The variable is called."
            }

            class ParameterCall(
                functionCall: Expression.FunctionCall,
                parameter: Function.Parameter,
            ) : TypeCheckingError(listOf(functionCall, parameter)) {
                override val errorMessage = "The parameter of a function is called."
            }

            class ConditionalTypesMismatch(
                conditional: Expression.Conditional,
                typeWhenTrue: Type,
                typeWhenFalse: Type,
            ) : TypeCheckingError(listOf(conditional)) {
                override val errorMessage = "The results of a conditional operator have distinct types: $typeWhenTrue and $typeWhenFalse"
            }

            class NonConstantExpression(
                expression: Expression,
            ) : TypeCheckingError(listOf(expression)) {
                override val errorMessage = "The expression is not constant (expected constant)."
            }

            class InvalidType(
                expression: Expression,
                type: Type,
                expectedType: Type,
            ) : TypeCheckingError(listOf(expression)) {
                override val errorMessage = "The type of the expression is $expectedType (expected $type)."
            }

            class MissingReturnStatement(
                function: Function,
            ) : TypeCheckingError(listOf(function)) {
                override val errorMessage = "The function neither returns unit, not has a 'return' statement."
            }
        }

        sealed class VariablePropertiesError(astNodes: List<AstNode>) : ResolutionError(astNodes) {
            override fun isError() = true

            class AssignmentToFunctionParameter(
                parameter: Function.Parameter,
                owner: Function,
                assignedIn: Function
            ) : VariablePropertiesError(listOf(owner, parameter, assignedIn)) {
                override val errorMessage = "The parameter is owned by the function ${owner.name} and is assigned in the function ${assignedIn.name}."
            }
        }

        sealed class ControlFlowDiagnostic(astNodes: List<AstNode>) : ResolutionError(astNodes) {

            sealed class Warnings(astNodes: List<AstNode>) : ControlFlowDiagnostic(astNodes) {
                override fun isError() = false

                class UnreachableStatement(
                    statement: Statement,
                ) : Warnings(listOf(statement)) {
                    override val errorMessage = "The statement is unreachable."
                }
            }

            sealed class Errors(astNodes: List<AstNode>) : ControlFlowDiagnostic(astNodes) {
                override fun isError() = true

                class BreakOutsideOfLoop(
                    loopBreak: Statement.LoopBreak,
                ) : Errors(listOf(loopBreak)) {
                    override val errorMessage = "The 'break' is used outside of a loop."
                }

                class ContinuationOutsideOfLoop(
                    loopContinuation: Statement.LoopContinuation,
                ) : Errors(listOf(loopContinuation)) {
                    override val errorMessage = "The 'continue' is used outside of a loop."
                }
            }
        }
    }
}
