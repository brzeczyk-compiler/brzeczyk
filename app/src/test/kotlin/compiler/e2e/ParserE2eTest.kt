package compiler.e2e

import compiler.diagnostics.Diagnostic
import compiler.e2e.E2eTestUtils.assertErrorOfType
import kotlin.test.Test

class ParserE2eTest {

    private fun assertParseError(programs: List<String>) {
        programs.forEach { assertErrorOfType(it, Diagnostic.ParserError.UnexpectedToken::class) }
    }

    @Test
    fun `test variable declaration`() {
        assertParseError(
            listOf(
                """
                        zm test: Liczba = Liczba
                        
                    """,
                """
                        wart test: Czy = ++
                        
                    """,
                """
                        stała test: Nic = ;
                    
                    """,
                """
                        zm test: Liczba = zm
                        
                    """,
                """
                        zm test: Liczba = )
                        
                    """,
                """
                        zm test: Liczba + 17
                        
                    """,
                """
                        zm test: Liczba 17
                        
                    """,
                """
                        zm test: 17 = 17
                        
                    """,
                """
                        zm test = 17
                        
                    """,
                """
                        zm: Liczba = 17
                        
                    """,
            )
        )
    }

    @Test
    fun `test function declaration`() {
        assertParseError(
            listOf(
                """
                            function f() { }
                            
                        """,
                """
                            czynność f()
                                { }
                            
                        """,
                """
                            czynność Nic() { }
                            
                        """,
                """
                            czynność f() -> Liczba
                            
                        """,
                """
                            czynność f(): Liczba { }
                            
                        """,
                """
                            czynność f() -> Liczba = 17
                            
                        """,
                """
                            czynność f(jaki: Napis) -> Liczba { }
                            
                        """,
                """
                            czynność f(nie_wiadomo) -> Liczba { }
                            
                        """,
                """
                            czynność f(nie_wiadomo_d = 17) -> Liczba { }
                            
                        """,
                """
                            czynność f() -> liczba { }
                            
                        """,
                """
                            czynność f(ile: Liczba = 17, czy: Czy) -> Liczba { }
                            
                        """,
                """
                            zew czynność f() { }
                        """,
                """
                            czynność zewnętrzna f()
                        """,
                """
                            czynność `abcd`()
                        """,
                """
                            zewnętrzna czynność F()
                        """,
                """
                            zewnętrzna czynność `F`()
                        """,
                """
                            zewnętrzna czynność `F`() jako G
                        """,
                """
                            zewnętrzna czynność f() jako `g`
                        """,
            )
        )
    }

    @Test
    fun `test if else`() {
        assertParseError(
            listOf(
                """
                            czynność f() {
                                jeśli (prawda) {
                            }
                            
                        """,
                """
                            czynność f() {
                                jeśli (prawda) {
                                wpp {
                                    napisz(1)
                                }
                            }
                            
                        """,
                """
                            czynność f() {
                                wpp {
                                    napisz(3)
                                }
                            }
                            
                        """,
                """
                            czynność f() {
                                zaś gdy (fałsz) {
                                    napisz(2)
                                } wpp {
                                    napisz(3)
                                }
                            }
                            
                        """,
                """
                            czynność f() {
                                jeśli {
                                    napisz(1)
                                } zaś gdy (fałsz) {
                                    napisz(2)
                                } wpp {
                                    napisz(3)
                                }
                            }
                            
                        """,
                """
                            czynność f() {
                                jeśli (prawda) {
                                    napisz(1)
                                } zaś gdy {
                                    napisz(2)
                                }
                            }
                            
                        """,
                """
                            czynność f() {
                                jeśli (prawda) {
                                    napisz(1)
                                } wpp (fałsz) {
                                    napisz(3)
                                }
                            }
                            
                        """,
                """
                            czynność f() {
                                jeśli () {
                                    napisz(1)
                                }
                            }
                            
                        """,
                """
                            czynność f() {
                                jeśli (zm a: Liczba = 10) {
                                    napisz(1)
                                }
                            }
                            
                        """,
                """
                            czynność f() {
                                jeśli (zwróć prawda) {
                                    napisz(1)
                                }
                            }
                            
                        """,
                """
                            czynność f() {
                                jeśli ( jeśli (prawda) { napisz(0)} ) {
                                    napisz(1)
                                }
                            }
                            
                        """,
                """
                            czynność f(x: Czy) -> Czy {
                                zwróć (
                                    jeśli (x) {
                                        x
                                    } wpp {
                                        nie x
                                    }
                                )
                            }
                            
                        """,
            )
        )
    }

    @Test
    fun `test while`() {
        assertParseError(
            listOf(
                """
                            czynność f() {
                                dopóki (prawda) {
                            }
                            
                        """,
                """
                            czynność f() {
                                dopóki () { }
                            }
                            
                        """,
                """
                            czynność f() {
                                dopóki (zwróć fałsz) { }
                            }
                            
                        """,
            )
        )
    }

    @Test
    fun `test return`() {
        assertParseError(
            listOf(
                """
                            czynność f() -> Liczba {
                                return 5
                            }
                            
                        """,
                """
                            czynność f() -> Liczba {
                                zwróć zm test: Liczba = 17
                            }
                            
                        """,
                """
                            czynność f() -> Liczba {
                                zakończ 17
                            }
                            
                        """,
                """
                            czynność f() {
                                zakończ program
                            }
                            
                        """,
                """
                            czynność f() {
                                zwróć
                            }
                            
                        """,
                """
                            czynność f() {
                                zwróć Nic
                            }
                            
                        """,
                """
                            przekaźnik f() {
                                przekaż
                            }
                            
                        """,
                """
                            przekaźnik f() {
                                przekaż Nic
                            }
                            
                        """,
            )
        )
    }

    @Test
    fun `test colons`() {
        assertParseError(
            listOf(
                """
                            zm test: Liczba = 17;;
                            
                        """,
                """
                            zm test; Liczba = 17
                            
                        """,
                """
                            czynność f(a: Liczba; b: Liczba) { }
                            
                        """,
                """
                            czynność f(a: Liczba,, b: Liczba) { }
                            
                        """,
                """
                            czynność f(a; Liczba) { }
                            
                        """,
            )
        )
    }

    @Test
    fun `test expressions`() {
        assertParseError(
            listOf(
                """
                            czynność f(x: Liczba) -> Liczba {
                                zwróć (x %;x)
                            }
                            
                        """,
                """
                            czynność f(x: Liczba) -> Liczba {
                                zwróć ()
                            }
                            
                        """,
                """
                            czynność f(x: Liczba) -> Liczba {
                                zwróć (x - x
                            }
                            
                        """,
                """
                            czynność f(x: Czy) -> Czy {
                                zwróć )x albo x(
                            }
                            
                        """,
                """
                            czynność f(x: Liczba) -> Czy {
                                zwróć {x >= x}
                            }
                            
                        """,
                """
                            czynność f(x: Liczba) -> Czy {
                                zwróć (x !== x)
                            }
                            
                        """,
                """
                            czynność f(x: Liczba) -> Czy {
                                zwróć (x === x)
                            }
                            
                        """,
                """
                            czynność f(x: Czy) -> Czy {
                                czynność g(y: Czy) -> Czy { }
                                zwróć g(x: Czy)
                            }
                            
                        """,
            )
        )
    }

    @Test
    fun `test operators`() {
        assertParseError(
            listOf(
                """
                            czynność f(x: Czy) -> Czy {
                                zwróć (x ? x ? x : x : x)
                            }
                            
                        """,
                """
                            czynność f() {
                                zm test: Liczba = 17
                                test := 18
                            }
                            
                        """,
                """
                            czynność f() {
                                zm test: Liczba = 17
                                test 
                                = 18
                            }
                            
                        """,
            )
        )
    }
}
