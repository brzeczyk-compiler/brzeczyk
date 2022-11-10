package compiler.common.diagnostics

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.lexer.Location

sealed class Diagnostic {
    abstract fun isError(): Boolean

    data class LexerError(val start: Location, val end: Location?, val context: List<String>, val errorSegment: String) : Diagnostic() {

        override fun isError() = true

        override fun toString() = StringBuilder()
            .append("Unable to match token at location $start - ${end ?: "eof"}.\n")
            .append("\t\t${context.joinToString("")}-->$errorSegment<---")
            .toString()
    }

    class ParserError(
        val symbol: Any?,
        val start: Location,
        val end: Location,
        val expectedSymbols: List<Any>
    ) : Diagnostic() {
        override fun isError() = true

        override fun toString() = StringBuilder().apply {
            if (symbol != null)
                append("Unexpected symbol $symbol at location $start - $end.")
            else
                append("Unexpected end of file.")
            if (expectedSymbols.isNotEmpty())
                append(" Expected symbols: ${expectedSymbols.joinToString()}.")
        }.toString()
    }

    sealed class TypeCheckingError : Diagnostic() {
        override fun isError() = true

        data class ConstantWithoutValue(val variable: Variable) : TypeCheckingError() {
            override fun toString() = "A constant must have a value"
        }

        data class UninitializedGlobalVariable(val variable: Variable) : TypeCheckingError() {
            override fun toString() = "A global variable must be initialized"
        }

        data class ImmutableAssignment(val assignment: Statement.Assignment, val variable: Variable) : TypeCheckingError() {
            override fun toString() = "Cannot assign to a " + if (variable.kind == Variable.Kind.CONSTANT) "constant" else "value"
        }

        data class ParameterAssignment(val assignment: Statement.Assignment, val parameter: Function.Parameter) : TypeCheckingError() {
            override fun toString() = "Cannot assign to a parameter"
        }

        data class FunctionAssignment(val assignment: Statement.Assignment, val function: Function) : TypeCheckingError() {
            override fun toString() = "Cannot assign to a function"
        }

        data class FunctionAsValue(val expression: Expression.Variable, val function: Function) : TypeCheckingError() {
            override fun toString() = "Cannot use a function as a value"
        }

        data class VariableCall(val call: Expression.FunctionCall, val variable: Variable) : TypeCheckingError() {
            override fun toString() = "Cannot call a variable"
        }

        data class ParameterCall(val call: Expression.FunctionCall, val parameter: Function.Parameter) : TypeCheckingError() {
            override fun toString() = "Cannot call a parameter"
        }

        data class ConditionalTypesMismatch(val conditional: Expression.Conditional, val typeWhenTrue: Type, val typeWhenFalse: Type) : TypeCheckingError() {
            override fun toString() = "The results of a conditional operator cannot have distinct types '$typeWhenTrue' and '$typeWhenFalse'"
        }

        data class NonConstantExpression(val expression: Expression) : TypeCheckingError() {
            override fun toString() = "Expected a constant expression"
        }

        data class InvalidType(val expression: Expression, val type: Type, val expectedType: Type) : TypeCheckingError() {
            override fun toString() = "Expected type '$expectedType' instead of '$type'"
        }
    }

    sealed class VariablePropertiesError() : Diagnostic() {
        override fun isError() = true

        data class AssignmentToOuterVariable(
            // Any = Variable | Function.Parameter
            val variable: Any,
            val owner: Function?,
            val assignedIn: Function
        ) : VariablePropertiesError() {
            override fun toString() = StringBuilder().apply {
                append("Assignment in inner function ${assignedIn.name} to ")
                var variableName = "Unknown variable"
                if (variable is Variable) variableName = variable.name
                if (variable is Function.Parameter) variableName = variable.name
                if (owner == null)
                    append("global variable $variableName")
                else
                    append("variable $variableName defined in function ${owner.name}")
            }.toString()
        }
        data class AssignmentToFunctionParameter(
            val parameter: Function.Parameter,
            val owner: Function,
            val assignedIn: Function
        ) : VariablePropertiesError() {
            override fun toString() = "Assignment to parameter ${parameter.name} " +
                "of type ${parameter.type} of function ${owner.name} in function ${assignedIn.name}"
        }
    }
}
