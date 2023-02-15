package compiler.intermediate

import compiler.analysis.ProgramAnalyzer
import compiler.analysis.VariablePropertiesAnalyzer
import compiler.ast.AstNode
import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.intermediate.generators.DISPLAY_LABEL_IN_MEMORY
import compiler.intermediate.generators.DefaultFunctionDetailsGenerator
import compiler.intermediate.generators.FunctionDetailsGenerator
import compiler.intermediate.generators.VariableLocationType
import compiler.utils.Ref
import compiler.utils.keyRefMapOf
import compiler.utils.refSetOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DetailsGeneratorsBuilderTest {
    @Test
    fun `test create unique identifiers one function`() {
        /*
         czynność no_polish_signs() {}
         */
        val identifierFactory = UniqueIdentifierFactory()
        val noPolishSigns = Function("no_polish_signs", listOf(), Type.Unit, listOf())
        val noPolishSignsIdentifier = identifierFactory.build(null, noPolishSigns.name)

        val program = Program(listOf(Program.Global.FunctionDefinition(noPolishSigns)))
        val actualIdentifiers = DetailsGeneratorsBuilder.createUniqueIdentifiers(program, false)
        val expectedIdentifiers = keyRefMapOf(noPolishSigns to noPolishSignsIdentifier)
        assertEquals(expectedIdentifiers, actualIdentifiers)
    }

    @Test fun `test create unique identifiers no polish signs`() {
        /*
         czynność polskie_znaki_są_żeś_zaiście_świetneż() {}
         */
        val identifierFactory = UniqueIdentifierFactory()
        val polishSigns = Function("polskie_znaki_są_żeś_zaiście_świetneż", listOf(), Type.Unit, listOf())
        val polishSignsIdentifier = identifierFactory.build(null, polishSigns.name)

        val program = Program(listOf(Program.Global.FunctionDefinition(polishSigns)))
        val actualIdentifiers = DetailsGeneratorsBuilder.createUniqueIdentifiers(program, false)
        val expectedIdentifiers = keyRefMapOf(polishSigns to polishSignsIdentifier)
        assertEquals(expectedIdentifiers, actualIdentifiers)
    }

    @Test fun `test create unique identifiers nested`() {
        /*
         czynność some_prefix() {
             czynność no_polish_signs() {}
         }
         */
        val identifierFactory = UniqueIdentifierFactory()
        val noPolishSigns = Function("no_polish_signs", listOf(), Type.Unit, listOf())
        val outerFunction = Function(
            "some_prefix",
            listOf(), Type.Unit, listOf(Statement.FunctionDefinition(noPolishSigns))
        )
        val outerFunctionIdentifier = identifierFactory.build(null, outerFunction.name)
        val noPolishSignsIdentifier = identifierFactory.build(outerFunctionIdentifier.value, noPolishSigns.name)

        val program = Program(listOf(Program.Global.FunctionDefinition(outerFunction)))
        val actualIdentifiers = DetailsGeneratorsBuilder.createUniqueIdentifiers(program, false)
        val expectedIdentifiers = keyRefMapOf(
            noPolishSigns to noPolishSignsIdentifier,
            outerFunction to outerFunctionIdentifier
        )
        assertEquals(expectedIdentifiers, actualIdentifiers)
    }

    @Test fun `test identical function names with accuracy to polish signs do not cause identifier conflict`() {
        /*
         czynność some_prefix() {
             czynność żeś() {}
             czynność żes() {}
             czynność zes() {}
         }
         */
        val identifierFactory = UniqueIdentifierFactory()
        val inner1 = Function("żeś", listOf(), Type.Unit, listOf())
        val inner2 = Function("żes", listOf(), Type.Unit, listOf())
        val inner3 = Function("zes", listOf(), Type.Unit, listOf())
        val outerFunction = Function(
            "some_prefix",
            listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(inner1), Statement.FunctionDefinition(inner2),
                Statement.FunctionDefinition(inner3)
            )
        )
        val outerFunctionIdentifier = identifierFactory.build(null, outerFunction.name)
        val inner1Identifier = identifierFactory.build(outerFunctionIdentifier.value, inner1.name)
        val inner2Identifier = identifierFactory.build(outerFunctionIdentifier.value, inner2.name)
        val inner3Identifier = identifierFactory.build(outerFunctionIdentifier.value, inner3.name)

        val program = Program(listOf(Program.Global.FunctionDefinition(outerFunction)))
        val actualIdentifiers = DetailsGeneratorsBuilder.createUniqueIdentifiers(program, false)
        val expectedIdentifiers = keyRefMapOf(
            inner1 to inner1Identifier,
            inner2 to inner2Identifier,
            inner3 to inner3Identifier,
            outerFunction to outerFunctionIdentifier
        )
        assertEquals(expectedIdentifiers, actualIdentifiers)
    }

    @Test fun `test function with name in forbidden memory label list is not assigned forbidden identifier`() {
        /*
         czynność globals() {}
         czynność display() {}
         */
        val identifierFactory = UniqueIdentifierFactory()
        for (forbidden in UniqueIdentifierFactory.forbiddenLabels) {
            val function = Function(forbidden, listOf(), Type.Unit, listOf())
            val functionIdentifier = identifierFactory.build(null, function.name)
            assertNotEquals(forbidden, functionIdentifier.value)
        }
    }

    @Test fun `test functions created in nested block receive identifiers`() {
        /*
         czynność some_prefix() {
             jeżeli (prawda) {
                 czynność funkcja1() {}
             }
             dopóki (prawda) {
                 czynność funkcja2() {}
             }
             {
                 czynność funkcja3() {}
             }
         }
         */
        val function = Function("funkcja1", listOf(), Type.Unit, listOf())
        val function2 = Function("funkcja2", listOf(), Type.Unit, listOf())
        val function3 = Function("funkcja3", listOf(), Type.Unit, listOf())
        val outerFunction = Function(
            "some_prefix",
            listOf(), Type.Unit,
            listOf(
                Statement.Conditional(
                    Expression.BooleanLiteral(true),
                    listOf(Statement.FunctionDefinition(function)), null
                ),
                Statement.Loop(
                    Expression.BooleanLiteral(true),
                    listOf(Statement.FunctionDefinition(function2)), null
                ),
                Statement.Block(
                    listOf(Statement.FunctionDefinition(function3)), null
                )
            )
        )

        val program = Program(listOf(Program.Global.FunctionDefinition(outerFunction)))
        val actualIdentifiers = DetailsGeneratorsBuilder.createUniqueIdentifiers(program, false)
        assertContains(actualIdentifiers, Ref(function))
        assertContains(actualIdentifiers, Ref(function2))
        assertContains(actualIdentifiers, Ref(function3))
        assertContains(actualIdentifiers, Ref(outerFunction))
    }

    @Test fun `test functions at same level blocks with identical names do not cause conflicts`() {
        /*
         czynność some_prefix() {
             jeżeli (prawda) {
                 czynność funkcja() {}
             } wpp {
                 czynność funkcja() {}
             }
             dopóki (prawda) {
                 czynność funkcja() {}
             }
             {
                 czynność funkcja() {}
             }
         }
         */
        val identifierFactory = UniqueIdentifierFactory()
        val function = Function("funkcja", listOf(), Type.Unit, listOf())
        val functionCopy1 = Function("funkcja", listOf(), Type.Unit, listOf())
        val functionCopy2 = Function("funkcja", listOf(), Type.Unit, listOf())
        val functionCopy3 = Function("funkcja", listOf(), Type.Unit, listOf())
        val outerFunction = Function(
            "some_prefix",
            listOf(), Type.Unit,
            listOf(
                Statement.Conditional(
                    Expression.BooleanLiteral(true),
                    listOf(Statement.FunctionDefinition(function)),
                    listOf(Statement.FunctionDefinition(functionCopy1))
                ),
                Statement.Loop(
                    Expression.BooleanLiteral(true),
                    listOf(Statement.FunctionDefinition(functionCopy2)), null
                ),
                Statement.Block(
                    listOf(Statement.FunctionDefinition(functionCopy3)), null
                )
            )
        )

        val program = Program(listOf(Program.Global.FunctionDefinition(outerFunction)))
        val actualIdentifiers = DetailsGeneratorsBuilder.createUniqueIdentifiers(program, false)
        val outerFunctionIdentifier = identifierFactory.build(null, outerFunction.name)
        val functionIdentifier = identifierFactory.build(outerFunctionIdentifier.value + "@block0", function.name)
        val functionCopy1Identifier = identifierFactory.build(outerFunctionIdentifier.value + "@block1", functionCopy1.name)
        val functionCopy2Identifier = identifierFactory.build(outerFunctionIdentifier.value + "@block2", functionCopy2.name)
        val functionCopy3Identifier = identifierFactory.build(outerFunctionIdentifier.value + "@block3", functionCopy3.name)
        val expectedIdentifiers = keyRefMapOf(
            outerFunction to outerFunctionIdentifier,
            function to functionIdentifier,
            functionCopy1 to functionCopy1Identifier,
            functionCopy2 to functionCopy2Identifier,
            functionCopy3 to functionCopy3Identifier,
        )
        assertEquals(expectedIdentifiers, actualIdentifiers)
    }

    @Test fun `test function details generator creation`() {
        /*
        czynność f() {
            zm a: Liczba = 4
            zm b: Czy = fałsz
            zm c: Liczba = 10
            czynność g(x: Liczba) {
                a = b ? 3 : 2
            }
        }
         */

        val varA = Variable(Variable.Kind.VARIABLE, "a", Type.Number, Expression.NumberLiteral(4))
        val varB = Variable(Variable.Kind.VARIABLE, "b", Type.Boolean, Expression.BooleanLiteral(false))
        val varC = Variable(Variable.Kind.VARIABLE, "c", Type.Number, Expression.NumberLiteral(10))
        val parameter = Function.Parameter("x", Type.Number, null)
        val functionG = Function(
            "g",
            listOf(parameter),
            Type.Unit,
            listOf(
                Statement.Assignment(
                    Statement.Assignment.LValue.Variable("a"),
                    Expression.Conditional(
                        Expression.Variable("b"),
                        Expression.NumberLiteral(3),
                        Expression.NumberLiteral(2)
                    )
                )
            )
        )
        val functionF = Function(
            "f",
            emptyList(),
            Type.Unit,
            listOf(
                Statement.VariableDefinition(varA),
                Statement.VariableDefinition(varB),
                Statement.VariableDefinition(varC),
                Statement.FunctionDefinition(functionG)
            )
        )
        val program = Program(listOf(Program.Global.FunctionDefinition(functionF)))
        val variableProperties = keyRefMapOf<AstNode, VariablePropertiesAnalyzer.VariableProperties>(
            parameter to VariablePropertiesAnalyzer.VariableProperties(functionG, refSetOf(), refSetOf()),
            varA to VariablePropertiesAnalyzer.VariableProperties(functionF, refSetOf(), refSetOf(functionG)),
            varB to VariablePropertiesAnalyzer.VariableProperties(functionF, refSetOf(functionG), refSetOf()),
            varC to VariablePropertiesAnalyzer.VariableProperties(functionF, refSetOf(), refSetOf())
        )

        val expectedResult = keyRefMapOf(
            functionF to DefaultFunctionDetailsGenerator(
                emptyList(),
                null,
                IFTNode.MemoryLabel("fun\$f"),
                0u,
                keyRefMapOf(varA to VariableLocationType.MEMORY, varB to VariableLocationType.MEMORY, varC to VariableLocationType.REGISTER),
                IFTNode.MemoryLabel(DISPLAY_LABEL_IN_MEMORY)
            ) as FunctionDetailsGenerator,
            functionG to DefaultFunctionDetailsGenerator(
                listOf(parameter),
                null,
                IFTNode.MemoryLabel("fun\$f\$g"),
                1u,
                keyRefMapOf(parameter to VariableLocationType.REGISTER),
                IFTNode.MemoryLabel(DISPLAY_LABEL_IN_MEMORY)
            )
        )

        val programProperties = ProgramAnalyzer.ProgramProperties(
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            variableProperties,
            emptyMap(),
            0
        )

        val actualResult = DetailsGeneratorsBuilder.createDetailsGenerators(program, programProperties).first
        assertEquals(expectedResult, actualResult)
    }

    @Test fun `test function details generator creation for function that returns variable`() {
        /*
        czynność f(): Liczba {
            zm a: Liczba = 4
            zwróć a+1
        }
         */

        val varA = Variable(Variable.Kind.VARIABLE, "a", Type.Number, Expression.NumberLiteral(4))
        val functionF = Function(
            "f",
            listOf(),
            Type.Number,
            listOf(
                Statement.VariableDefinition(varA),
                Statement.FunctionReturn(
                    Expression.BinaryOperation(
                        Expression.BinaryOperation.Kind.ADD,
                        Expression.Variable("a"),
                        Expression.NumberLiteral(1)
                    )
                )
            )
        )

        val returnVariable = Variable(Variable.Kind.VARIABLE, "_return_dummy_", Type.Number, null)

        val program = Program(listOf(Program.Global.FunctionDefinition(functionF)))
        val variableProperties = keyRefMapOf<AstNode, VariablePropertiesAnalyzer.VariableProperties>(
            varA to VariablePropertiesAnalyzer.VariableProperties(functionF, refSetOf(), refSetOf()),
            returnVariable to VariablePropertiesAnalyzer.VariableProperties(functionF, refSetOf(), refSetOf()),
        )

        val expectedResult = keyRefMapOf(
            functionF to DefaultFunctionDetailsGenerator(
                emptyList(),
                returnVariable,
                IFTNode.MemoryLabel("fun\$f"),
                0u,
                keyRefMapOf(varA to VariableLocationType.REGISTER, returnVariable to VariableLocationType.REGISTER),
                IFTNode.MemoryLabel(DISPLAY_LABEL_IN_MEMORY)
            ) as FunctionDetailsGenerator
        )

        val programProperties = ProgramAnalyzer.ProgramProperties(
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            keyRefMapOf(functionF to returnVariable),
            variableProperties,
            emptyMap(),
            0
        )

        val actualResult = DetailsGeneratorsBuilder.createDetailsGenerators(program, programProperties).first
        assertEquals(expectedResult, actualResult)
    }
}
