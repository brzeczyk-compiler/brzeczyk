package compiler.e2e

import compiler.common.diagnostics.Diagnostic
import compiler.e2e.E2eAsserter.assertErrorOfType
import kotlin.test.Test

class ParserErrorsTest {

    fun assertParseError(programs: List<String>) {
        programs.forEach { assertErrorOfType(it, Diagnostic.ParserError::class) }
    }

    @Test
    fun `test assignment`() {
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
                                zwróć Nic
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
}