package compiler.ast

import compiler.input.LocationRange

data class Function(
    override val name: String,
    val parameters: List<Parameter>,
    val returnType: Type,
    val implementation: Implementation,
    val isGenerator: Boolean,
    override val location: LocationRange? = null,
) : NamedNode, AstNode, VariableOwner {
    data class Parameter(
        override val name: String,
        val type: Type,
        val defaultValue: Expression?,
        override val location: LocationRange? = null,
    ) : NamedNode, AstNode

    sealed class Implementation {
        data class Local(val body: StatementBlock) : Implementation()
        data class Foreign(val foreignName: String) : Implementation()
    }

    val isLocal: Boolean get() = implementation is Implementation.Local

    val body: StatementBlock get() = if (implementation is Implementation.Local) implementation.body else emptyList()

    constructor(
        name: String,
        parameters: List<Parameter>,
        returnType: Type,
        body: StatementBlock,
        isGenerator: Boolean = false,
        location: LocationRange? = null
    ) : this(name, parameters, returnType, Implementation.Local(body), isGenerator, location)
}
