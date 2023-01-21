package compiler.syntax

import compiler.Compiler.CompilationFailed
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.StatementBlock
import compiler.ast.Type
import compiler.ast.Variable
import compiler.diagnostics.Diagnostic
import compiler.diagnostics.Diagnostics
import compiler.input.LocationRange
import compiler.parser.ParseTree
import compiler.regex.RegexDfa
import compiler.syntax.LanguageGrammar.Productions
import compiler.syntax.utils.TokenRegexParser

class AstFactory(private val diagnostics: Diagnostics) {
    class AstCreationFailed : CompilationFailed()

    // helper methods
    private fun ParseTree<Symbol>.token(): TokenType? = (symbol as? Symbol.Terminal)?.tokenType
    private fun ParseTree<Symbol>.nonTerm(): NonTerminalType? = (symbol as? Symbol.NonTerminal)?.nonTerminal

    private fun combineLocations(parseTree1: ParseTree<Symbol>, parseTree2: ParseTree<Symbol>): LocationRange = LocationRange(parseTree1.location.start, parseTree2.location.end)
    private fun combineLocations(parseTrees: List<ParseTree<Symbol>>): LocationRange = LocationRange(parseTrees.first().location.start, parseTrees.last().location.end)

    private fun ParseTree.Branch<Symbol>.getFilteredChildren(): List<ParseTree<Symbol>> = children.filter {
        it.token() !in listOf(TokenType.NEWLINE, TokenType.SEMICOLON)
    }

    private fun skipPassThroughExpressions(parseTree: ParseTree.Branch<Symbol>): ParseTree.Branch<Symbol> {
        return if (parseTree.nonTerm() in listOf(NonTerminalType.EXPR4096, NonTerminalType.E_EXPR4096) || parseTree.children.size > 1)
            parseTree
        else
            skipPassThroughExpressions(parseTree.children.first() as ParseTree.Branch)
    }

    private fun extractIdentifier(parseTree: ParseTree.Branch<Symbol>): String {
        val properNode = skipPassThroughExpressions(parseTree)
        if (properNode.production !in listOf(Productions.expr4096Identifier, Productions.eExpr4096Identifier)) {
            diagnostics.report(
                Diagnostic.ParserError.UnexpectedToken(
                    properNode.symbol,
                    properNode.location,
                    listOf(Symbol.Terminal(TokenType.IDENTIFIER)),
                )
            )
            throw AstCreationFailed()
        }
        return (properNode.children.first() as ParseTree.Leaf).content
    }

    private fun extractLValue(parseTree: ParseTree.Branch<Symbol>, diagnostics: Diagnostics): Statement.Assignment.LValue {
        return when (val expr = processExpression(parseTree)) {
            is Expression.Variable -> Statement.Assignment.LValue.Variable(expr.name)
            is Expression.ArrayElement -> Statement.Assignment.LValue.ArrayElement(expr.expression, expr.index)
            else -> {
                diagnostics.report(
                    Diagnostic.ParserError.UnexpectedToken(
                        parseTree.symbol,
                        parseTree.location,
                        listOf(Symbol.Terminal(TokenType.IDENTIFIER)),
                    )
                )
                throw AstCreationFailed()
            }
        }
    }

    private val identifierRegexDfa = RegexDfa(TokenRegexParser.parseStringToRegex("""\l[\l\u\d_]*"""))

    private fun extractForeignName(parseTree: ParseTree.Leaf<Symbol>, asIdentifier: Boolean): String {
        return when (parseTree.token()) {
            TokenType.IDENTIFIER -> parseTree.content
            TokenType.FOREIGN_NAME -> {
                val identifier = parseTree.content.substring(1, parseTree.content.lastIndex)
                if (asIdentifier) {
                    val walk = identifierRegexDfa.newWalk()
                    identifier.forEach { walk.step(it) }
                    if (walk.getAcceptingStateTypeOrNull() === null) {
                        diagnostics.report(Diagnostic.ParserError.ForeignNameAsInvalidIdentifier(identifier, parseTree.location))
                        throw AstCreationFailed()
                    }
                }
                identifier
            }
            else -> throw IllegalArgumentException()
        }
    }

    private fun rotateExpressionLeft(parseTree: ParseTree.Branch<Symbol>): ParseTree.Branch<Symbol> {
        val children = parseTree.getFilteredChildren()
        val leftChild = children[0]
        val rightChild = children[2]
        val grandchildren = (rightChild as ParseTree.Branch).getFilteredChildren()
        if (grandchildren.size != 3)
            return parseTree
        val leftGrandchild = grandchildren[0]
        val rightGrandchild = grandchildren[2]

        val newLeftChild = ParseTree.Branch(combineLocations(leftChild, leftGrandchild), parseTree.symbol, listOf(leftChild, children[1], leftGrandchild), parseTree.production)
        val newTopLevelNode = ParseTree.Branch(combineLocations(leftChild, rightGrandchild), rightChild.symbol, listOf(newLeftChild, grandchildren[1], rightGrandchild), rightChild.production)

        return rotateExpressionLeft(newTopLevelNode)
    }

    // node processor methods
    fun createFromParseTree(parseTree: ParseTree<Symbol>): Program {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()
        val globals = children.map {
            when (it.nonTerm()) {
                NonTerminalType.VAR_DECL -> Program.Global.VariableDefinition(processVariableDeclaration(it), it.location)
                NonTerminalType.FUNC_DEF -> Program.Global.FunctionDefinition(processFunctionDefinition(it), it.location)
                NonTerminalType.FOREIGN_DECL -> Program.Global.FunctionDefinition(processForeignFunctionDeclaration(it), it.location)
                else -> throw IllegalArgumentException()
            }
        }
        return Program(globals)
    }

    private fun processType(parseTree: ParseTree<Symbol>): Type {
        return when ((parseTree as ParseTree.Branch).children[0].token()) {
            TokenType.TYPE_INTEGER -> Type.Number
            TokenType.TYPE_BOOLEAN -> Type.Boolean
            TokenType.TYPE_UNIT -> Type.Unit
            TokenType.TYPE_STRING -> Type.Array(Type.Number)
            TokenType.LEFT_BRACKET -> Type.Array(
                processType(parseTree.children[1])
            )
            else -> throw IllegalArgumentException()
        }
    }

    private fun processConst(parseTree: ParseTree<Symbol>): Expression {
        val child = (parseTree as ParseTree.Branch).children[0]

        return when (child.token()) {
            TokenType.INTEGER -> Expression.NumberLiteral(
                (child as ParseTree.Leaf).content.let {
                    try {
                        it.toLong()
                    } catch (_: NumberFormatException) {
                        diagnostics.report(Diagnostic.ParserError.InvalidNumberLiteral(it, child.location))
                        0
                    }
                },
                child.location,
            )

            TokenType.STRING -> {
                val characters: MutableList<Long> = mutableListOf()
                val string = (child as ParseTree.Leaf).content

                var i = 1
                while (i < string.length - 1) {
                    if (string[i] == '\\') {
                        characters.add(
                            when (string[i + 1]) {
                                'n' -> '\n'
                                't' -> '\t'
                                '\\' -> '\\'
                                'b' -> '\b'
                                'r' -> '\r'
                                '”' -> '”'
                                else -> {
                                    diagnostics.report(
                                        Diagnostic.ParserError.UnexpectedToken(
                                            parseTree.symbol,
                                            parseTree.location,
                                            listOf(Symbol.Terminal(TokenType.STRING)),
                                        )
                                    )
                                    throw AstCreationFailed()
                                }
                            }.code.toLong()
                        )
                        i += 2
                    } else {
                        characters.add(string[i].code.toLong())
                        i += 1
                    }
                }

                Expression.ArrayAllocation(
                    Type.Number,
                    Expression.NumberLiteral(characters.size.toLong()),
                    characters.map { char -> Expression.NumberLiteral(char) },
                    Expression.ArrayAllocation.InitializationType.ALL_VALUES
                )
            }

            TokenType.TRUE_CONSTANT -> Expression.BooleanLiteral(true, child.location)
            TokenType.FALSE_CONSTANT -> Expression.BooleanLiteral(false, child.location)
            TokenType.UNIT_CONSTANT -> Expression.UnitLiteral(child.location)
            else -> throw IllegalArgumentException()
        }
    }

    private fun processVariableDeclaration(parseTree: ParseTree<Symbol>): Variable {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        val kind = when (children[0].token()) {
            TokenType.VARIABLE -> Variable.Kind.VARIABLE
            TokenType.VALUE -> Variable.Kind.VALUE
            TokenType.CONSTANT -> Variable.Kind.CONSTANT
            else -> throw IllegalArgumentException()
        }
        val name = (children[1] as ParseTree.Leaf).content
        val type = processType(children[3])
        val value = if (children.lastIndex == 5) processExpression(children[5]) else null

        return Variable(kind, name, type, value, combineLocations(children))
    }

    private fun processFunctionDefinition(parseTree: ParseTree<Symbol>): Function {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        val isGenerator = children[0].token() == TokenType.GENERATOR
        val name = (children[1] as ParseTree.Leaf).content
        val parameters = processFunctionDefinitionParameters(children[3])
        val returnType = if (children[5].token() == TokenType.ARROW) processType(children[6]) else Type.Unit
        val body = processManyStatements(children[children.lastIndex - 1])
        return Function(name, parameters, returnType, body, isGenerator, combineLocations(children))
    }

    private fun processForeignFunctionDeclaration(parseTree: ParseTree<Symbol>): Function {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        val isGenerator = children[1].token() == TokenType.GENERATOR
        val foreignName = extractForeignName(children[2] as ParseTree.Leaf<Symbol>, false)
        val parameters = processFunctionDefinitionParameters(children[4])
        val returnType = if (children.size > 6 && children[6].token() == TokenType.ARROW) processType(children[7]) else Type.Unit
        val localName = if (children.size > 6 && children[children.size - 2].token() == TokenType.AS)
            (children[children.size - 1] as ParseTree.Leaf).content
        else
            extractForeignName(children[2] as ParseTree.Leaf<Symbol>, true)
        return Function(localName, parameters, returnType, Function.Implementation.Foreign(foreignName), isGenerator, parseTree.location)
    }

    private fun processFunctionDefinitionParameters(parseTree: ParseTree<Symbol>): List<Function.Parameter> {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()
        val parameters = ArrayList<Function.Parameter>()
        var it = 0
        while (it < children.size) {
            val grandchildren = (children[it] as ParseTree.Branch).getFilteredChildren()
            val name = (grandchildren[0] as ParseTree.Leaf).content
            val type = processType(grandchildren[2])
            val defaultValue = if (it + 1 < children.size && children[it + 1].token() == TokenType.ASSIGNMENT) {
                it += 4
                processExpression(children[it - 2])
            } else {
                it += 2
                null
            }
            parameters.add(Function.Parameter(name, type, defaultValue, combineLocations(grandchildren[0], grandchildren[2])))
        }

        return parameters
    }

    private fun processFunctionCallArguments(parseTree: ParseTree<Symbol>): List<Expression.FunctionCall.Argument> {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()
        val arguments = ArrayList<Expression.FunctionCall.Argument>()

        var it = 0
        while (it < children.size) {
            if (it + 1 < children.size && children[it + 1].token() == TokenType.ASSIGNMENT) {
                val name = extractIdentifier(children[it] as ParseTree.Branch)
                val value = processExpression(children[it + 2])
                arguments.add(Expression.FunctionCall.Argument(name, value, combineLocations(children[it], children[it + 2])))
                it += 4
            } else {
                val value = processExpression(children[it])
                arguments.add(Expression.FunctionCall.Argument(null, value, children[it].location))
                it += 2
            }
        }

        return arguments
    }

    private val unaryOperations = mapOf(
        Productions.expr2048UnaryPlus to Expression.UnaryOperation.Kind.PLUS,
        Productions.eExpr2048UnaryPlus to Expression.UnaryOperation.Kind.PLUS,
        Productions.expr2048UnaryMinus to Expression.UnaryOperation.Kind.MINUS,
        Productions.eExpr2048UnaryMinus to Expression.UnaryOperation.Kind.MINUS,
        Productions.expr2048UnaryBoolNot to Expression.UnaryOperation.Kind.NOT,
        Productions.eExpr2048UnaryBoolNot to Expression.UnaryOperation.Kind.NOT,
        Productions.expr2048UnaryBitNot to Expression.UnaryOperation.Kind.BIT_NOT,
        Productions.eExpr2048UnaryBitNot to Expression.UnaryOperation.Kind.BIT_NOT,
    )

    private val binaryOperations = mapOf(
        Productions.expr2BoolOr to Expression.BinaryOperation.Kind.OR,
        Productions.eExpr2BoolOr to Expression.BinaryOperation.Kind.OR,
        Productions.expr4BoolAnd to Expression.BinaryOperation.Kind.AND,
        Productions.eExpr4BoolAnd to Expression.BinaryOperation.Kind.AND,
        Productions.expr8BoolXor to Expression.BinaryOperation.Kind.XOR,
        Productions.eExpr8BoolXor to Expression.BinaryOperation.Kind.XOR,
        Productions.expr8BoolIff to Expression.BinaryOperation.Kind.IFF,
        Productions.eExpr8BoolIff to Expression.BinaryOperation.Kind.IFF,
        Productions.expr16Equal to Expression.BinaryOperation.Kind.EQUALS,
        Productions.eExpr16Equal to Expression.BinaryOperation.Kind.EQUALS,
        Productions.expr16NotEqual to Expression.BinaryOperation.Kind.NOT_EQUALS,
        Productions.eExpr16NotEqual to Expression.BinaryOperation.Kind.NOT_EQUALS,
        Productions.expr16LessThan to Expression.BinaryOperation.Kind.LESS_THAN,
        Productions.eExpr16LessThan to Expression.BinaryOperation.Kind.LESS_THAN,
        Productions.expr16LessOrEq to Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS,
        Productions.eExpr16LessOrEq to Expression.BinaryOperation.Kind.LESS_THAN_OR_EQUALS,
        Productions.expr16GreaterThan to Expression.BinaryOperation.Kind.GREATER_THAN,
        Productions.eExpr16GreaterThan to Expression.BinaryOperation.Kind.GREATER_THAN,
        Productions.expr16GreaterOrEq to Expression.BinaryOperation.Kind.GREATER_THAN_OR_EQUALS,
        Productions.eExpr16GreaterOrEq to Expression.BinaryOperation.Kind.GREATER_THAN_OR_EQUALS,
        Productions.expr32BitOr to Expression.BinaryOperation.Kind.BIT_OR,
        Productions.eExpr32BitOr to Expression.BinaryOperation.Kind.BIT_OR,
        Productions.expr64BitXor to Expression.BinaryOperation.Kind.BIT_XOR,
        Productions.eExpr64BitXor to Expression.BinaryOperation.Kind.BIT_XOR,
        Productions.expr128BitAnd to Expression.BinaryOperation.Kind.BIT_AND,
        Productions.eExpr128BitAnd to Expression.BinaryOperation.Kind.BIT_AND,
        Productions.expr256ShiftLeft to Expression.BinaryOperation.Kind.BIT_SHIFT_LEFT,
        Productions.eExpr256ShiftLeft to Expression.BinaryOperation.Kind.BIT_SHIFT_LEFT,
        Productions.expr256ShiftRight to Expression.BinaryOperation.Kind.BIT_SHIFT_RIGHT,
        Productions.eExpr256ShiftRight to Expression.BinaryOperation.Kind.BIT_SHIFT_RIGHT,
        Productions.expr512Plus to Expression.BinaryOperation.Kind.ADD,
        Productions.eExpr512Plus to Expression.BinaryOperation.Kind.ADD,
        Productions.expr512Minus to Expression.BinaryOperation.Kind.SUBTRACT,
        Productions.eExpr512Minus to Expression.BinaryOperation.Kind.SUBTRACT,
        Productions.expr1024Multiply to Expression.BinaryOperation.Kind.MULTIPLY,
        Productions.eExpr1024Multiply to Expression.BinaryOperation.Kind.MULTIPLY,
        Productions.expr1024Divide to Expression.BinaryOperation.Kind.DIVIDE,
        Productions.eExpr1024Divide to Expression.BinaryOperation.Kind.DIVIDE,
        Productions.expr1024Modulo to Expression.BinaryOperation.Kind.MODULO,
        Productions.eExpr1024Modulo to Expression.BinaryOperation.Kind.MODULO
    )

    private fun processExpression(parseTree: ParseTree<Symbol>): Expression {
        val properNode = skipPassThroughExpressions(parseTree as ParseTree.Branch)
        val children = properNode.getFilteredChildren()

        return when (properNode.production) {
            in listOf(Productions.exprTernary, Productions.eExprTernary) -> {
                val conditionExpr = processExpression(children[0])
                val trueBranchExpr = processExpression(children[2])
                val falseBranchExpr = processExpression(children[4])
                Expression.Conditional(conditionExpr, trueBranchExpr, falseBranchExpr, combineLocations(children))
            }
            in binaryOperations.keys -> {
                val rotatedNode = rotateExpressionLeft(properNode)
                val newChildren = rotatedNode.getFilteredChildren()
                val leftExpr = processExpression(newChildren[0])
                val rightExpr = processExpression(newChildren[2])
                Expression.BinaryOperation(binaryOperations.getValue(rotatedNode.production), leftExpr, rightExpr, combineLocations(children))
            }
            in unaryOperations.keys -> {
                val subExpr = processExpression(children[1])
                Expression.UnaryOperation(unaryOperations.getValue(properNode.production), subExpr, combineLocations(children))
            }
            in listOf(Productions.expr4096Call, Productions.eExpr4096Call) -> {
                val name = (children[0] as ParseTree.Leaf).content
                val args = processFunctionCallArguments(children[2])
                Expression.FunctionCall(name, args, combineLocations(children))
            }
            in listOf(Productions.expr4096Identifier, Productions.eExpr4096Identifier) ->
                Expression.Variable((children[0] as ParseTree.Leaf).content, combineLocations(children))
            in listOf(Productions.expr4096Const, Productions.eExpr4096Const) ->
                processConst(children[0])
            in listOf(Productions.expr4096Parenthesis, Productions.eExpr4096Parenthesis) ->
                processExpression(children[1])
            in listOf(Productions.expr2048ArrayAccess, Productions.eExpr2048ArrayAccess) -> {
                var expr = processExpression(children[0])
                var i = 2
                while (i < children.size) {
                    val index = processExpression(children[i])
                    expr = Expression.ArrayElement(expr, index, combineLocations(children.subList(0, i + 2)))
                    i += 3
                }
                return expr
            }
            in listOf(Productions.expr2048ArrayLength, Productions.eExpr2048ArrayLength) ->
                Expression.ArrayLength(processExpression(children[1]), combineLocations(children))
            in listOf(Productions.expr4096ArrayListAllocation, Productions.eExpr4096ArrayListAllocation) -> {
                val type = processType(children[1])
                val expressions = mutableListOf<Expression>()
                var i = 3
                while (i < children.size) {
                    expressions.add(processExpression(children[i]))
                    i += 2
                }
                val size = Expression.NumberLiteral(expressions.size.toLong())
                Expression.ArrayAllocation(type, size, expressions, Expression.ArrayAllocation.InitializationType.ALL_VALUES, combineLocations(children))
            }
            in listOf(Productions.expr4096ArrayDefaultAllocation, Productions.eExpr4096ArrayDefaultAllocation) -> {
                val type = processType(children[1])
                val size = processExpression(children[3])
                val defaultValue = processExpression(children[6])
                Expression.ArrayAllocation(type, size, listOf(defaultValue), Expression.ArrayAllocation.InitializationType.ONE_VALUE, combineLocations(children))
            }
            else -> throw IllegalArgumentException()
        }
    }

    private fun processManyStatements(parseTree: ParseTree<Symbol>): StatementBlock {
        return (parseTree as ParseTree.Branch).getFilteredChildren().map { processStatement(it) }
    }

    private fun processMaybeBlock(parseTree: ParseTree<Symbol>): StatementBlock {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        return when (parseTree.production) {
            in listOf(Productions.maybeBlockNonBrace, Productions.nonIfMaybeBlockNonBrace) ->
                listOf(processStatement(children[0]))
            in listOf(Productions.maybeBlockBraces, Productions.nonIfMaybeBlockBraces) ->
                processManyStatements(children[1])
            else -> throw IllegalArgumentException()
        }
    }

    private fun processIfStatement(parseTree: ParseTree<Symbol>): Statement {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        val segments = ArrayList<Pair<Expression, StatementBlock>>()
        var it = 0
        while (it < children.size && children[it].token() in listOf(TokenType.IF, TokenType.ELSE_IF)) {
            val conditionExpr = processExpression(children[it + 2])
            val bodyBlock = processMaybeBlock(children[it + 4])
            segments.add(Pair(conditionExpr, bodyBlock))
            it += 5
        }
        val elseSegment = if (it < children.size) processMaybeBlock(children[it + 1]) else null

        return segments.slice(0 until segments.lastIndex).foldRight(
            Statement.Conditional(segments.last().first, segments.last().second, elseSegment, combineLocations(children))
        ) { segment, elseBranch -> Statement.Conditional(segment.first, segment.second, listOf(elseBranch), combineLocations(children)) }
    }

    private fun processStatement(parseTree: ParseTree<Symbol>): Statement {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        return when (parseTree.production) {
            Productions.statementNonBrace ->
                processStatement(children[0])
            Productions.statementBraces ->
                Statement.Block(processManyStatements(children[1]), combineLocations(children))
            in listOf(Productions.nonBraceStatementAtomic, Productions.nonIfNonBraceStatementAtomic) ->
                processAtomicStatement(children[0])
            Productions.nonBraceStatementIf ->
                processIfStatement(parseTree)
            in listOf(Productions.nonBraceStatementWhile, Productions.nonIfNonBraceStatementWhile) -> {
                val conditionExpr = processExpression(children[2])
                val bodyBlock = processMaybeBlock(children[4])
                Statement.Loop(conditionExpr, bodyBlock, combineLocations(children))
            }
            in listOf(Productions.nonBraceStatementForEach, Productions.nonIfNonBraceStatementForEach) -> {
                val receivingVariable = Variable(
                    Variable.Kind.VALUE,
                    (children[1] as ParseTree.Leaf).content,
                    processType(children[3]),
                    null,
                    combineLocations(children.subList(1, 4)),
                )
                val generatorCall = Expression.FunctionCall(
                    (children[5] as ParseTree.Leaf).content,
                    processFunctionCallArguments(children[7]),
                    combineLocations(children.subList(5, 9)),
                )
                val action = processMaybeBlock(children[9])
                Statement.ForeachLoop(receivingVariable, generatorCall, action, combineLocations(children))
            }
            in listOf(Productions.nonBraceStatementFuncDef, Productions.nonIfNonBraceStatementFuncDef) ->
                Statement.FunctionDefinition(processFunctionDefinition(children[0]), combineLocations(children))
            else -> throw IllegalArgumentException()
        }
    }

    private fun processAtomicStatement(parseTree: ParseTree<Symbol>): Statement {
        val children = (parseTree as ParseTree.Branch).getFilteredChildren()

        return when (parseTree.production) {
            Productions.atomicExpr ->
                Statement.Evaluation(processExpression(children[0]), combineLocations(children))
            Productions.atomicAssignment -> {
                val lhsLValue = extractLValue(children[0] as ParseTree.Branch, diagnostics)
                val rhsExpr = processExpression(children[2])
                Statement.Assignment(lhsLValue, rhsExpr, combineLocations(children))
            }
            Productions.atomicBreak ->
                Statement.LoopBreak(combineLocations(children))
            Productions.atomicContinue ->
                Statement.LoopContinuation(combineLocations(children))
            Productions.atomicReturnUnit ->
                Statement.FunctionReturn(Expression.UnitLiteral(combineLocations(children)), true, combineLocations(children))
            Productions.atomicReturn ->
                Statement.FunctionReturn(processExpression(children[1]), false, combineLocations(children))
            Productions.atomicYield ->
                Statement.GeneratorYield(processExpression(children[1]), combineLocations(children))
            Productions.atomicVarDef ->
                Statement.VariableDefinition(processVariableDeclaration(children[0]), combineLocations(children))
            Productions.atomicForeignDecl ->
                Statement.FunctionDefinition(processForeignFunctionDeclaration(children[0]), combineLocations(children))
            else -> throw IllegalArgumentException()
        }
    }
}
