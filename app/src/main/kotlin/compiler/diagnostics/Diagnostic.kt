package compiler.diagnostics

import com.google.common.base.Objects
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.input.Location
import compiler.input.LocationRange
import compiler.intermediate.MAIN_FUNCTION_IDENTIFIER
import compiler.utils.Ref

sealed interface Diagnostic {
    val isError: Boolean
    val message: String

    data class LexerError(
        val start: Location,
        val end: Location?,
        val context: List<String>,
        val errorSegment: String,
    ) : Diagnostic {
        override val isError get() = true

        override val message get() = StringBuilder().apply {
            append("Lexer Error\n")
            append("The token at location $start - ${end ?: "eof"} is unexpected.\n")
            append("Context: \t\t${context.joinToString("")}-->$errorSegment<---\n")
        }.toString()
    }

    sealed class ParserError : Diagnostic {
        override val isError get() = true

        abstract val errorMessage: String

        override val message get(): String = "Parser Error\n${errorMessage}\n"

        data class UnexpectedToken(
            val symbol: Any?,
            val location: LocationRange,
            val expectedSymbols: List<Any?>,
        ) : ParserError() {
            override val errorMessage get() = StringBuilder().apply {
                if (symbol != null) append("The symbol <<$symbol>> is unexpected at location $location.")
                else append("The end of file is unexpected.")
                append("\n")
                append("Expected one of: ${expectedSymbols.joinToString { it.toString() }}.")
            }.toString()
        }

        data class InvalidNumberLiteral(
            val number: String,
            val location: LocationRange,
        ) : ParserError() {
            override val errorMessage get() = "The literal <<$number>> at location $location is an invalid number literal."
        }

        data class ForeignNameAsInvalidIdentifier(
            val foreignName: String,
            val location: LocationRange
        ) : ParserError() {
            override val errorMessage get() = "Foreign name <<$foreignName>> at location $location is not a valid identifier."
        }
    }

    sealed class ResolutionDiagnostic(private val astNodes: List<AstNode>) : Diagnostic {

        data class ObjectAssociatedToError(val message: String, val location: LocationRange?)

        private val associatedObjects get() = astNodes.map { ObjectAssociatedToError(it.toExtendedString(), it.location) }

        abstract val errorMessage: String

        override val message get() = StringBuilder().apply {
            if (isError) append("Resolution Error") else append("Resolution Warning")
            append("\n")
            associatedObjects.forEach { append(it.message).append("\n") }
            append(errorMessage).append("\n")
        }.toString()

        override fun equals(other: Any?): Boolean = other is ResolutionDiagnostic &&
            other.javaClass == javaClass &&
            other.astNodes.map(::Ref) == astNodes.map(::Ref)

        override fun hashCode(): Int = Objects.hashCode(javaClass, astNodes.map(::Ref))

        sealed class NameResolutionError(astNodes: List<AstNode>) : ResolutionDiagnostic(astNodes) {
            override val isError get() = true

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
                vararg nodesWithSameName: NamedNode
            ) : NameResolutionError(nodesWithSameName.asList()) {
                override val errorMessage = "There is a naming conflict."
            }

            class VariableIsNotCallable(
                variableOrParameter: NamedNode,
                functionCall: Expression.FunctionCall,
            ) : NameResolutionError(listOf(variableOrParameter, functionCall)) {
                override val errorMessage = "The variable is called."
            }

            class CallableIsNotVariable(
                function: Function,
                variable: Expression.Variable,
            ) : NameResolutionError(listOf(function, variable)) {
                override val errorMessage = "The callable is used as a variable."
            }

            class AssignmentToCallable(
                function: Function,
                assignment: Statement.Assignment,
            ) : NameResolutionError(listOf(function, assignment)) {
                override val errorMessage = "The callable is a left operand of the assignment."
            }

            class FunctionUsedAsAGenerator(
                function: Function,
                functionCall: Expression.FunctionCall,
            ) : NameResolutionError(listOf(function, functionCall)) {
                override val errorMessage = "Function called where generator call was expected"
            }

            class GeneratorUsedAsFunction(
                generator: Function,
                generatorCall: Expression.FunctionCall,
            ) : NameResolutionError(listOf(generator, generatorCall)) {
                override val errorMessage = "Generator called where function call was expected"
            }
        }

        sealed class ArgumentResolutionError(astNodes: List<AstNode>) : ResolutionDiagnostic(astNodes) {
            override val isError get() = true

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

        sealed class VariableInitializationError(astNodes: List<AstNode>) : ResolutionDiagnostic(astNodes) {
            override val isError get() = true

            class ReferenceToUninitializedVariable(
                variable: NamedNode
            ) : VariableInitializationError(listOf(variable)) {
                override val errorMessage = "A (potentially) uninitialized variable cannot be referenced."
            }
        }

        sealed class TypeCheckingError(astNodes: List<AstNode>) : ResolutionDiagnostic(astNodes) {
            override val isError get() = true

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
                override val errorMessage = "The function neither returns unit, nor has a 'return' statement."
            }

            class ReturnWithValueInGenerator(
                returnStatement: Statement.FunctionReturn,
            ) : TypeCheckingError(listOf(returnStatement)) {
                override val errorMessage = "The generator returns with value."
            }

            class YieldInNonGeneratorFunction(
                yieldStatement: Statement.GeneratorYield,
            ) : TypeCheckingError(listOf(yieldStatement)) {
                override val errorMessage = "The yield occurs in non-generator function."
            }
        }

        sealed class VariablePropertiesError(astNodes: List<AstNode>) : ResolutionDiagnostic(astNodes) {
            override val isError get() = true

            class AssignmentToFunctionParameter(
                parameter: Function.Parameter,
                owner: Function,
                assignedIn: Function
            ) : VariablePropertiesError(listOf(owner, parameter, assignedIn)) {
                override val errorMessage = "The parameter is owned by the function ${owner.name} and is assigned in the function ${assignedIn.name}."
            }
        }

        sealed class ControlFlowDiagnostic(astNodes: List<AstNode>) : ResolutionDiagnostic(astNodes) {

            sealed class Warnings(astNodes: List<AstNode>) : ControlFlowDiagnostic(astNodes) {
                override val isError get() = false

                class UnreachableStatement(
                    statement: Statement,
                ) : Warnings(listOf(statement)) {
                    override val errorMessage = "The statement is unreachable."
                }
            }

            sealed class Errors(astNodes: List<AstNode>) : ControlFlowDiagnostic(astNodes) {
                override val isError get() = true

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

        object MainFunctionNotFound : ResolutionDiagnostic(emptyList()) {
            override val isError: Boolean = true
            override val errorMessage = "Main function '$MAIN_FUNCTION_IDENTIFIER' not found."
        }
    }
}
