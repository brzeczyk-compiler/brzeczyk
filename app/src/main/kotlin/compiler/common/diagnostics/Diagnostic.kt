package compiler.common.diagnostics

import compiler.ast.Function
import compiler.ast.Variable
import compiler.lexer.Location

sealed class Diagnostic {
    abstract fun isError(): Boolean

    data class LexerError(val start: Location, val end: Location?, val context: List<String>, val errorSegment: String) : Diagnostic() {

        override fun isError(): Boolean = true

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
