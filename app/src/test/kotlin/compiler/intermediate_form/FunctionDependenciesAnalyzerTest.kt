package compiler.intermediate_form

import compiler.ast.Expression
import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Program
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceMap
import compiler.common.reference_collections.referenceMapOf
import compiler.common.reference_collections.referenceSetOf
import compiler.semantic_analysis.VariablePropertiesAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals

class FunctionDependenciesAnalyzerTest {
    @Test fun `test create unique identifiers one function`() {
        /*
         czynność no_polish_signs() {}
         */
        var noPolishSigns = Function("no_polish_signs", listOf(), Type.Unit, listOf())

        val program = Program(listOf(Program.Global.FunctionDefinition(noPolishSigns)))
        val actualIdentifiers = FunctionDependenciesAnalyzer.createUniqueIdentifiers(program)
        val expectedIdentifiers = referenceMapOf(noPolishSigns to UniqueIdentifier("no_polish_signs"))
        assertEquals(expectedIdentifiers, actualIdentifiers)
    }

    @Test fun `test create unique identifiers no polish signs`() {
        /*
         czynność polskie_znaki_są_żeś_zaiście_świetneż() {}
         */
        var polishSigns = Function("polskie_znaki_są_żeś_zaiście_świetneż", listOf(), Type.Unit, listOf())

        val program = Program(listOf(Program.Global.FunctionDefinition(polishSigns)))
        val actualIdentifiers = FunctionDependenciesAnalyzer.createUniqueIdentifiers(program)
        val expectedIdentifiers = referenceMapOf(polishSigns to UniqueIdentifier("polskie_znaki_sa_zes_zaiscie_swietnez"))
        assertEquals(expectedIdentifiers, actualIdentifiers)
    }

    @Test fun `test create unique identifiers nested`() {
        /*
         czynność some_prefix() {
             czynność no_polish_signs() {}
         }
         */
        var noPolishSigns = Function("no_polish_signs", listOf(), Type.Unit, listOf())
        var outerFunction = Function(
            "some_prefix",
            listOf(), Type.Unit, listOf(Statement.FunctionDefinition(noPolishSigns))
        )

        val program = Program(listOf(Program.Global.FunctionDefinition(outerFunction)))
        val actualIdentifiers = FunctionDependenciesAnalyzer.createUniqueIdentifiers(program)
        val expectedIdentifiers = referenceMapOf(
            noPolishSigns to UniqueIdentifier("some_prefix|no_polish_signs"),
            outerFunction to UniqueIdentifier("some_prefix")
        )
        assertEquals(expectedIdentifiers, actualIdentifiers)
    }

    @Test fun `test create unique identifiers conflicts`() {
        /*
         czynność some_prefix() {
             czynność żeś() {}
             czynność żes() {}
             czynność zes() {}
         }
         */
        val inner1 = Function("żeś", listOf(), Type.Unit, listOf())
        val inner2 = Function("żes", listOf(), Type.Unit, listOf())
        val inner3 = Function("zes", listOf(), Type.Unit, listOf())
        var outerFunction = Function(
            "some_prefix",
            listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(inner1), Statement.FunctionDefinition(inner2),
                Statement.FunctionDefinition(inner3)
            )
        )
        val program = Program(listOf(Program.Global.FunctionDefinition(outerFunction)))
        val actualIdentifiers = FunctionDependenciesAnalyzer.createUniqueIdentifiers(program)
        val expectedIdentifiers = referenceMapOf(
            inner1 to UniqueIdentifier("some_prefix|zes"),
            inner2 to UniqueIdentifier("some_prefix|zes_"),
            inner3 to UniqueIdentifier("some_prefix|zes__"),
            outerFunction to UniqueIdentifier("some_prefix")
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
        val par = Function.Parameter("x", Type.Number, null)
        val functionG = Function(
            "g",
            listOf(par),
            Type.Unit,
            listOf(
                Statement.Assignment(
                    "a",
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
        val variableProperties = referenceMapOf<Any, VariablePropertiesAnalyzer.VariableProperties>(
            par to VariablePropertiesAnalyzer.VariableProperties(functionG, referenceSetOf(), referenceSetOf()),
            varA to VariablePropertiesAnalyzer.VariableProperties(functionF, referenceSetOf(), referenceSetOf(functionG)),
            varB to VariablePropertiesAnalyzer.VariableProperties(functionF, referenceSetOf(functionG), referenceSetOf()),
            varC to VariablePropertiesAnalyzer.VariableProperties(functionF, referenceSetOf(), referenceSetOf())
        )

        val expectedResult = referenceMapOf(
            functionF to FunctionDetailsGenerator(
                0u,
                referenceMapOf(varA to true, varB to true, varC to false),
                emptyList()
            ),
            functionG to FunctionDetailsGenerator(1u, referenceMapOf(par to false), listOf(par))
        )

        val actualResult = FunctionDependenciesAnalyzer.createFunctionDetailsGenerators(program, variableProperties)

        assertEquals(expectedResult, actualResult)
    }

    @Test fun `test a function that does not call`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() { }

        */

        val fFunction = Function("f", listOf(), Type.Unit, listOf())
        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf()
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf<Function>(),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test a function that calls another function`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() { }

        czynność g() {
            f()
        }

        */

        val fFunctionCall = Expression.FunctionCall("f", listOf())
        val gFunction = Function("g", listOf(), Type.Unit, listOf(Statement.Evaluation(fFunctionCall)))
        val fFunction = Function("f", listOf(), Type.Unit, listOf())
        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
            Program.Global.FunctionDefinition(gFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(fFunctionCall to fFunction)
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(),
            gFunction to referenceSetOf(fFunction),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test inner functions`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() {
            czynność g() { }

            czynność h() {
                g()
            }
        }

        */

        val gFunctionCall = Expression.FunctionCall("g", listOf())
        val hFunction = Function("h", listOf(), Type.Unit, listOf(Statement.Evaluation(gFunctionCall)))
        val gFunction = Function("g", listOf(), Type.Unit, listOf())
        val fFunction = Function(
            "f", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(gFunction),
                Statement.FunctionDefinition(hFunction),
            )
        )
        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(gFunctionCall to gFunction)
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(),
            gFunction to referenceSetOf(),
            hFunction to referenceSetOf(gFunction),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test recursion`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() {
            f()
        }

        */

        val fFunctionCall = Expression.FunctionCall("f", listOf())
        val fFunction = Function("f", listOf(), Type.Unit, listOf(Statement.Evaluation(fFunctionCall)))
        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(fFunctionCall to fFunction)
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(fFunction),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test transitivity`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() { }

        czynność g() {
            f()
        }

        czynność h() {
            g()
        }

        */

        val gFunctionCall = Expression.FunctionCall("g", listOf())
        val fFunctionCall = Expression.FunctionCall("f", listOf())
        val hFunction = Function("h", listOf(), Type.Unit, listOf(Statement.Evaluation(gFunctionCall)))
        val gFunction = Function("g", listOf(), Type.Unit, listOf(Statement.Evaluation(fFunctionCall)))
        val fFunction = Function("f", listOf(), Type.Unit, listOf())
        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
            Program.Global.FunctionDefinition(gFunction),
            Program.Global.FunctionDefinition(hFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(
            fFunctionCall to fFunction,
            gFunctionCall to gFunction,
        )
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(),
            gFunction to referenceSetOf(fFunction),
            hFunction to referenceSetOf(fFunction, gFunction),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test a cycle`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() {
            i()
        }

        czynność g() {
            f()
        }

        czynność h() {
            g()
        }

        czynność i() {
            h()
        }

        */

        val hFunctionCall = Expression.FunctionCall("h", listOf())
        val gFunctionCall = Expression.FunctionCall("g", listOf())
        val fFunctionCall = Expression.FunctionCall("f", listOf())
        val iFunctionCall = Expression.FunctionCall("i", listOf())

        val iFunction = Function("h", listOf(), Type.Unit, listOf(Statement.Evaluation(hFunctionCall)))
        val hFunction = Function("h", listOf(), Type.Unit, listOf(Statement.Evaluation(gFunctionCall)))
        val gFunction = Function("g", listOf(), Type.Unit, listOf(Statement.Evaluation(fFunctionCall)))
        val fFunction = Function("f", listOf(), Type.Unit, listOf(Statement.Evaluation(iFunctionCall)))

        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
            Program.Global.FunctionDefinition(gFunction),
            Program.Global.FunctionDefinition(hFunction),
            Program.Global.FunctionDefinition(iFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(
            fFunctionCall to fFunction,
            gFunctionCall to gFunction,
            hFunctionCall to hFunction,
            iFunctionCall to iFunction,
        )
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(fFunction, gFunction, hFunction, iFunction),
            gFunction to referenceSetOf(fFunction, gFunction, hFunction, iFunction),
            hFunction to referenceSetOf(fFunction, gFunction, hFunction, iFunction),
            iFunction to referenceSetOf(fFunction, gFunction, hFunction, iFunction),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test buried recursion`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() {
            czynność g() {
                czynność h() {
                    czynność i() {
                        f()
                    }
                    i()
                }
                h()
            }
            g()
        }

        */

        val gFunctionCall = Expression.FunctionCall("g", listOf())
        val hFunctionCall = Expression.FunctionCall("h", listOf())
        val iFunctionCall = Expression.FunctionCall("i", listOf())
        val fFunctionCall = Expression.FunctionCall("f", listOf())

        val iFunction = Function("i", listOf(), Type.Unit, listOf(Statement.Evaluation(fFunctionCall)))
        val hFunction = Function(
            "h", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(iFunction),
                Statement.Evaluation(iFunctionCall)
            ),
        )
        val gFunction = Function(
            "g", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(hFunction),
                Statement.Evaluation(hFunctionCall),
            )
        )
        val fFunction = Function(
            "f", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(gFunction),
                Statement.Evaluation(gFunctionCall),
            )
        )

        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(
            fFunctionCall to fFunction,
            gFunctionCall to gFunction,
            hFunctionCall to hFunction,
            iFunctionCall to iFunction,
        )
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(fFunction, gFunction, hFunction, iFunction),
            gFunction to referenceSetOf(fFunction, gFunction, hFunction, iFunction),
            hFunction to referenceSetOf(fFunction, gFunction, hFunction, iFunction),
            iFunction to referenceSetOf(fFunction, gFunction, hFunction, iFunction),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test operators`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() -> Liczba {
            zwróć 17
        }
        czynność g() { }
        czynność h() { }
        czynność i() -> Czy {
            zwróć prawda
        }
        czynność j() { }
        czynność k() { }

        czynność test() {
            + f()
            g() == h()
            i() ? j() : k()
        }

        */

        val fFunctionCall = Expression.FunctionCall("f", listOf())
        val gFunctionCall = Expression.FunctionCall("g", listOf())
        val hFunctionCall = Expression.FunctionCall("h", listOf())
        val iFunctionCall = Expression.FunctionCall("i", listOf())
        val jFunctionCall = Expression.FunctionCall("j", listOf())
        val kFunctionCall = Expression.FunctionCall("k", listOf())

        val fFunction = Function("f", listOf(), Type.Number, listOf(Statement.FunctionReturn(Expression.NumberLiteral(17))))
        val gFunction = Function("g", listOf(), Type.Unit, listOf())
        val hFunction = Function("h", listOf(), Type.Unit, listOf())
        val iFunction = Function("i", listOf(), Type.Boolean, listOf(Statement.FunctionReturn(Expression.BooleanLiteral(true))))
        val jFunction = Function("j", listOf(), Type.Unit, listOf())
        val kFunction = Function("k", listOf(), Type.Unit, listOf())
        val testFunction = Function(
            "test", listOf(), Type.Unit,
            listOf(
                Statement.Evaluation(Expression.UnaryOperation(Expression.UnaryOperation.Kind.PLUS, fFunctionCall)),
                Statement.Evaluation(Expression.BinaryOperation(Expression.BinaryOperation.Kind.EQUALS, gFunctionCall, hFunctionCall)),
                Statement.Evaluation(Expression.Conditional(iFunctionCall, jFunctionCall, kFunctionCall)),
            )
        )
        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
            Program.Global.FunctionDefinition(gFunction),
            Program.Global.FunctionDefinition(hFunction),
            Program.Global.FunctionDefinition(iFunction),
            Program.Global.FunctionDefinition(jFunction),
            Program.Global.FunctionDefinition(kFunction),
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(
            fFunctionCall to fFunction,
            gFunctionCall to gFunction,
            hFunctionCall to hFunction,
            iFunctionCall to iFunction,
            jFunctionCall to jFunction,
            kFunctionCall to kFunction,
        )
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(),
            gFunction to referenceSetOf(),
            hFunction to referenceSetOf(),
            iFunction to referenceSetOf(),
            jFunction to referenceSetOf(),
            kFunction to referenceSetOf(),
            testFunction to referenceSetOf(fFunction, gFunction, hFunction, iFunction, jFunction, kFunction),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test variable definition and assignment`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() -> Liczba {
            zwróć 17
        }
        czynność g() -> Liczba {
            zwróć 18
        }

        czynność test() {
            wart x: Liczba = f()
            zm y: Liczba
            y = g()
        }

        */

        val fFunctionCall = Expression.FunctionCall("f", listOf())
        val gFunctionCall = Expression.FunctionCall("g", listOf())

        val yAssignment = Statement.Assignment("y", gFunctionCall)
        val xVariable = Variable(Variable.Kind.VALUE, "x", Type.Number, fFunctionCall)
        val yVariable = Variable(Variable.Kind.VARIABLE, "y", Type.Number, null)

        val fFunction = Function("f", listOf(), Type.Number, listOf(Statement.FunctionReturn(Expression.NumberLiteral(17))))
        val gFunction = Function("g", listOf(), Type.Number, listOf(Statement.FunctionReturn(Expression.NumberLiteral(18))))
        val testFunction = Function(
            "test", listOf(), Type.Unit,
            listOf(
                Statement.VariableDefinition(xVariable),
                Statement.VariableDefinition(yVariable),
                yAssignment,
            )
        )
        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
            Program.Global.FunctionDefinition(gFunction),
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(
            fFunctionCall to fFunction,
            gFunctionCall to gFunction,
            yAssignment to yVariable,
        )
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(),
            gFunction to referenceSetOf(),
            testFunction to referenceSetOf(fFunction, gFunction),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test control flow`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() { }
        czynność g() -> Czy {
            zwróć prawda
        }
        czynność h() { }
        czynność i() { }
        czynność j() -> Czy {
            zwróć prawda
        }
        czynność k() { }
        czynność l() -> Liczba {
            zwróć 17
        }

        czynność test() -> Liczba {
            {
                f()
            }

            jeżeli (g()) {
                h()
            } wpp {
                i()
            }

            dopóki(j()) {
                k()
            }

            zwróć l()
        }

        */

        val fFunctionCall = Expression.FunctionCall("f", listOf())
        val gFunctionCall = Expression.FunctionCall("g", listOf())
        val hFunctionCall = Expression.FunctionCall("h", listOf())
        val iFunctionCall = Expression.FunctionCall("i", listOf())
        val jFunctionCall = Expression.FunctionCall("j", listOf())
        val kFunctionCall = Expression.FunctionCall("k", listOf())
        val lFunctionCall = Expression.FunctionCall("l", listOf())

        val fFunction = Function("f", listOf(), Type.Unit, listOf())
        val gFunction = Function("g", listOf(), Type.Boolean, listOf(Statement.FunctionReturn(Expression.BooleanLiteral(true))))
        val hFunction = Function("h", listOf(), Type.Unit, listOf())
        val iFunction = Function("i", listOf(), Type.Unit, listOf())
        val jFunction = Function("j", listOf(), Type.Boolean, listOf(Statement.FunctionReturn(Expression.BooleanLiteral(true))))
        val kFunction = Function("k", listOf(), Type.Unit, listOf())
        val lFunction = Function("l", listOf(), Type.Number, listOf(Statement.FunctionReturn(Expression.NumberLiteral(17))))
        val testFunction = Function(
            "test", listOf(), Type.Number,
            listOf(
                Statement.Block(listOf(Statement.Evaluation(fFunctionCall))),
                Statement.Conditional(gFunctionCall, listOf(Statement.Evaluation(hFunctionCall)), listOf(Statement.Evaluation(iFunctionCall))),
                Statement.Loop(jFunctionCall, listOf(Statement.Evaluation(kFunctionCall))),
                Statement.FunctionReturn(lFunctionCall),
            )
        )
        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
            Program.Global.FunctionDefinition(gFunction),
            Program.Global.FunctionDefinition(hFunction),
            Program.Global.FunctionDefinition(iFunction),
            Program.Global.FunctionDefinition(jFunction),
            Program.Global.FunctionDefinition(kFunction),
            Program.Global.FunctionDefinition(lFunction),
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(
            fFunctionCall to fFunction,
            gFunctionCall to gFunction,
            hFunctionCall to hFunction,
            iFunctionCall to iFunction,
            jFunctionCall to jFunction,
            kFunctionCall to kFunction,
            lFunctionCall to lFunction,
        )
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(),
            gFunction to referenceSetOf(),
            hFunction to referenceSetOf(),
            iFunction to referenceSetOf(),
            jFunction to referenceSetOf(),
            kFunction to referenceSetOf(),
            lFunction to referenceSetOf(),
            testFunction to referenceSetOf(fFunction, gFunction, hFunction, iFunction, jFunction, kFunction, lFunction),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test arguments`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f(x: Liczba, y: Czy) -> Liczba {
            zwróć 17
        }
        czynność g(z: Liczba) -> Liczba {
            zwróć 18
        }
        czynność h() -> Liczba {
            zwróć 19
        }
        czynność i() -> Czy {
            zwróć fałsz
        }

        czynność test() {
            f(g(h()), i())
        }

        */

        val fFunction = Function(
            "f",
            listOf(
                Function.Parameter("x", Type.Number, null),
                Function.Parameter("y", Type.Boolean, null),
            ),
            Type.Number, listOf(Statement.FunctionReturn(Expression.NumberLiteral(17)))
        )
        val gFunction = Function(
            "g",
            listOf(
                Function.Parameter("z", Type.Number, null),
            ),
            Type.Number, listOf(Statement.FunctionReturn(Expression.NumberLiteral(18)))
        )
        val hFunction = Function("h", listOf(), Type.Number, listOf(Statement.FunctionReturn(Expression.NumberLiteral(19))))
        val iFunction = Function("l", listOf(), Type.Boolean, listOf(Statement.FunctionReturn(Expression.BooleanLiteral(false))))

        val iFunctionCall = Expression.FunctionCall("i", listOf())
        val hFunctionCall = Expression.FunctionCall("h", listOf())
        val gFunctionCall = Expression.FunctionCall(
            "g",
            listOf(
                Expression.FunctionCall.Argument("z", hFunctionCall),
            )
        )
        val fFunctionCall = Expression.FunctionCall(
            "f",
            listOf(
                Expression.FunctionCall.Argument("x", gFunctionCall),
                Expression.FunctionCall.Argument("y", iFunctionCall),
            )
        )
        val testFunction = Function(
            "test", listOf(), Type.Number,
            listOf(
                Statement.Evaluation(fFunctionCall)
            )
        )
        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
            Program.Global.FunctionDefinition(gFunction),
            Program.Global.FunctionDefinition(hFunction),
            Program.Global.FunctionDefinition(iFunction),
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(
            fFunctionCall to fFunction,
            gFunctionCall to gFunction,
            hFunctionCall to hFunction,
            iFunctionCall to iFunction,
        )
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(),
            gFunction to referenceSetOf(),
            hFunction to referenceSetOf(),
            iFunction to referenceSetOf(),
            testFunction to referenceSetOf(fFunction, gFunction, hFunction, iFunction),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test default arguments`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() -> Liczba {
            zwróć 17
        }

        czynność test() {
            czynność g(x: Liczba, y: Liczba = f()) { }
        }

        */

        val fFunctionCall = Expression.FunctionCall("f", listOf())

        val fFunction = Function("f", listOf(), Type.Number, listOf(Statement.FunctionReturn(Expression.NumberLiteral(17))))
        val gFunction = Function(
            "g",
            listOf(
                Function.Parameter("x", Type.Number, null),
                Function.Parameter("y", Type.Number, fFunctionCall),
            ),
            Type.Unit, listOf()
        )
        val testFunction = Function(
            "test", listOf(), Type.Number,
            listOf(
                Statement.FunctionDefinition(gFunction),
            )
        )
        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
            Program.Global.FunctionDefinition(gFunction),
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(
            fFunctionCall to fFunction,
        )
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(),
            gFunction to referenceSetOf(),
            testFunction to referenceSetOf(fFunction),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test comparison by reference`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność f() {
            czynność p() { }
            p()
        }

        czynność g() {
            czynność p() { }
            p()
        }

        */

        val pfFunctionCall = Expression.FunctionCall("p", listOf())
        val pgFunctionCall = Expression.FunctionCall("p", listOf())

        val pfFunction = Function("p", listOf(), Type.Unit, listOf())
        val pgFunction = Function("p", listOf(), Type.Unit, listOf())

        val fFunction = Function(
            "f", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(pfFunction),
                Statement.Evaluation(pfFunctionCall),
            )
        )
        val gFunction = Function(
            "g", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(pgFunction),
                Statement.Evaluation(pgFunctionCall),
            )
        )
        val globals = listOf(
            Program.Global.FunctionDefinition(fFunction),
            Program.Global.FunctionDefinition(gFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(
            pfFunctionCall to pfFunction,
            pgFunctionCall to pgFunction,
        )
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(pfFunction),
            gFunction to referenceSetOf(pgFunction),
            pfFunction to referenceSetOf(),
            pgFunction to referenceSetOf(),
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }

    @Test fun `test comparison by reference - sets`() {
        /*
        Create AST for program:
        ---------------------------------

        czynność test() {
            czynność f() {
                czynność p() { }
                p()
            }
            f()

            czynność g() {
                czynność p() { }
                p()
            }
            g()
        }

        */

        val fFunctionCall = Expression.FunctionCall("f", listOf())
        val gFunctionCall = Expression.FunctionCall("g", listOf())
        val pfFunctionCall = Expression.FunctionCall("p", listOf())
        val pgFunctionCall = Expression.FunctionCall("p", listOf())

        val pfFunction = Function("p", listOf(), Type.Unit, listOf())
        val pgFunction = Function("p", listOf(), Type.Unit, listOf())

        val fFunction = Function(
            "f", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(pfFunction),
                Statement.Evaluation(pfFunctionCall),
            )
        )
        val gFunction = Function(
            "g", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(pgFunction),
                Statement.Evaluation(pgFunctionCall),
            )
        )
        val testFunction = Function(
            "test", listOf(), Type.Unit,
            listOf(
                Statement.FunctionDefinition(fFunction),
                Statement.Evaluation(fFunctionCall),
                Statement.FunctionDefinition(gFunction),
                Statement.Evaluation(gFunctionCall),
            )
        )
        val globals = listOf(
            Program.Global.FunctionDefinition(testFunction),
        )

        val program = Program(globals)
        val nameResolution: ReferenceMap<Any, NamedNode> = referenceMapOf(
            pfFunctionCall to pfFunction,
            pgFunctionCall to pgFunction,
            fFunctionCall to fFunction,
            gFunctionCall to gFunction,
        )
        val actualCallGraph = FunctionDependenciesAnalyzer.createCallGraph(program, nameResolution)

        val expectedCallGraph = referenceMapOf(
            fFunction to referenceSetOf(pfFunction),
            gFunction to referenceSetOf(pgFunction),
            pfFunction to referenceSetOf(),
            pgFunction to referenceSetOf(),
            testFunction to referenceSetOf(fFunction, gFunction, pfFunction, pgFunction)
        )

        assertEquals(expectedCallGraph, actualCallGraph)
    }
}
