package compiler.e2e

import compiler.common.diagnostics.Diagnostic
import compiler.e2e.E2eAsserter.assertErrorOfType
import org.junit.Ignore
import org.junit.Test

class TypeCheckingErrorsTest {
    fun assertTypeCheckingError(program: String) {
        assertErrorOfType(program, Diagnostic.TypeCheckingError::class)
    }

    @Ignore
    @Test
    fun `test define variable with unit type`() {
        assertTypeCheckingError("zm a: Nic;")
        assertTypeCheckingError("wart a: Nic;")
        assertTypeCheckingError("stała a: Nic;")
    }

    @Ignore
    @Test
    fun `test function parameter with unit type`() {
        assertTypeCheckingError(
            """
            czynność f(a: Nic) {
                zakończ
            }
            """
        )
    }

    @Ignore
    @Test
    fun `test instantiate variable with wrong type`() {
        assertTypeCheckingError("zm a: Liczba = prawda;")
        assertTypeCheckingError("zm a: Liczba = fałsz;")
        assertTypeCheckingError("zm a: Liczba = (5 > 3) lub 0 == -1;")
        assertTypeCheckingError("zm b: Czy = fałsz; zm a: Liczba = b;")
        assertTypeCheckingError(
            """
                czynność f() -> Czy {
                    zwróć prawda
                }
                
                czynność g() {
                    zm a: Liczba = f()
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zakończ
                }
                
                czynność g() {
                    zm a: Liczba = f()
                }
            """
        )

        assertTypeCheckingError("zm a: Czy = 0;")
        assertTypeCheckingError("zm a: Czy = fałsz;")
        assertTypeCheckingError("zm a: Czy = 5 * 3 - (1 % 2);")
        assertTypeCheckingError("zm b: Liczba = 4; zm a: Czy = b;")
        assertTypeCheckingError(
            """
                czynność f() -> Liczba {
                    zwróć 3
                }
                
                czynność g() {
                    zm a: Czy = f()
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zakończ
                }
                
                czynność g() {
                    zm a: Czy = f()
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test assignment to variable of wrong type`() {
        assertTypeCheckingError(
            """
                czynność f() {
                    zm a: Liczba = 1 
                    a = prawda
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zm a: Liczba = 1
                    a = fałsz
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zm a: Liczba = 1
                    a = (5 > 3) lub 0 == -1
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zm b: Czy = fałsz
                    zm a: Liczba
                    a = b
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() -> Czy {
                    zwróć prawda
                }
                czynność g() {
                    zm a: Liczba
                    a = f()
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    zm a: Liczba
                    a = f()
                }
            """
        )

        assertTypeCheckingError(
            """
                czynność f() {
                    zm a: Czy
                    a = 0
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zm a: Czy
                    a = -13
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zm a: Czy
                    a = 5 * 3 - (1 % 2)
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zm b: Liczba = 4; 
                    zm a: Czy
                    a = b
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() -> Liczba {
                    zwróć 3
                }
                czynność g() {
                    zm a: Czy
                    a = f()
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    zm a: Czy
                    a = f()
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test integer operators with wrong types`() {
        val binaryOperators = listOf("+", "-", "*", "/", "%", "^", "&", "|", "<<", ">>")
        val unaryOperators = listOf("-") // , "~")

        for (operator in binaryOperators) {
            assertTypeCheckingError(
                """
                    zm a: Liczba = 4
                    zm b: Czy = prawda
                    zm c: Liczba = a $operator b;
                """
            )
            assertTypeCheckingError(
                """
                    zm a: Liczba = 4
                    zm b: Czy = prawda
                    zm c: Liczba = b $operator a;
                """
            )
            assertTypeCheckingError(
                """
                    czynność f() {
                        zakończ 
                    }
                    zm b: Czy = prawda
                    zm c: Liczba = f() $operator b;
                """
            )
            assertTypeCheckingError(
                """
                    czynność f() {
                        zakończ 
                    }
                    zm a: Liczba = 4
                    zm c: Liczba = b $operator f();
                """
            )
        }

        for (operator in unaryOperators) {
            assertTypeCheckingError(
                """
                    zm b: Czy = prawda
                    zm c: Liczba = $operator b;
                """
            )
            assertTypeCheckingError(
                """
                    czynność f() {
                        zakończ 
                    }
                    zm c: Liczba = $operator f();
                """
            )
        }
    }

    @Ignore
    @Test
    fun `test boolean operators with wrong types`() {
        val binaryOperators = listOf("oraz", "lub", "wtw", "albo")
        val unaryOperators = emptyList<String>() // listOf("nie")

        for (operator in binaryOperators) {
            assertTypeCheckingError(
                """
                    zm a: Liczba = 4
                    zm b: Czy = prawda
                    zm c: Czy = a $operator b;
                """
            )
            assertTypeCheckingError(
                """
                    zm a: Liczba = 4
                    zm b: Czy = prawda
                    zm c: Czy = b $operator a;
                """
            )
            assertTypeCheckingError(
                """
                    czynność f() {
                        zakończ 
                    }
                    zm b: Czy = prawda
                    zm c: Czy = f() $operator b;
                """
            )
            assertTypeCheckingError(
                """
                    czynność f() {
                        zakończ 
                    }
                    zm a: Liczba = 4
                    zm c: Czy = b $operator f();
                """
            )
        }

        for (operator in unaryOperators) {
            assertTypeCheckingError(
                """
                    zm b: Liczba = 17
                    zm c: Czy = $operator b;
                """
            )
            assertTypeCheckingError(
                """
                    czynność f() {
                        zakończ 
                    }
                    zm c: Czy = $operator f();
                """
            )
        }
    }

    @Ignore
    @Test
    fun `test comparison operators with wrong types`() {
        val comparisonOperators = listOf("==", "!=", ">", ">=", "<", "<=")

        for (operator in comparisonOperators) {
            assertTypeCheckingError(
                """
                    zm a: Liczba = 4
                    zm b: Czy = prawda
                    zm c: Czy = a $operator b;
                """
            )
            assertTypeCheckingError(
                """
                    zm a: Liczba = 4
                    zm b: Czy = prawda
                    zm c: Czy = b $operator a;
                """
            )
            assertTypeCheckingError(
                """
                    czynność f() {
                        zakończ 
                    }
                    zm b: Czy = prawda
                    zm c: Czy = f() $operator b;
                """
            )
            assertTypeCheckingError(
                """
                    czynność f() {
                        zakończ 
                    }
                    zm a: Liczba = 4
                    zm c: Czy = b $operator f();
                """
            )
        }
    }

    @Ignore
    @Test
    fun `test conditional operator with wrong types`() {
        assertTypeCheckingError("wart a: Liczba = prawda ? 10 : fałsz;")
        assertTypeCheckingError("wart a: Liczba = prawda ? prawda : 13;")
        assertTypeCheckingError("wart a: Liczba = 34 ? fałsz : 14;")
        assertTypeCheckingError("wart a: Liczba = 34 ? 10 : 31;")
        assertTypeCheckingError("wart a: Liczba = 99 ? prawda : fałsz;")
        assertTypeCheckingError("wart a: Czy = fałsz ? 11 : 16;")
        assertTypeCheckingError(
            """
            czynność f() {
                zakończ
            }
            czynność g() {
                f() ? 10 : 11                            
            }
            """
        )
        assertTypeCheckingError(
            """
            czynność f() {
                zakończ
            }
            czynność g() {
                prawda ? f() : 11                            
            }
            """
        )
        assertTypeCheckingError(
            """
            czynność f() {
                zakończ
            }
            czynność g() {
                prawda ? 12 : f()                   
            }
            """
        )
        assertTypeCheckingError(
            """
            czynność f() {
                zakończ
            }
            czynność g() {
                prawda ? fałsz : f()                   
            }
            """
        )
    }

    @Ignore
    @Test
    fun `test wrong return type`() {
        assertTypeCheckingError(
            """
                czynność f() {
                    zwróć 4                            
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zwróć fałsz                            
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() -> Liczba {
                    zakończ                            
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() -> Liczba {
                    zwróć fałsz                            
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() -> Czy {
                    zakończ                            
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() -> Czy {
                    zwróć 654
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test wrong default parameter type`() {
        assertTypeCheckingError(
            """
                czynność f(a: Liczba = fałsz) {
                    zakończ
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f(a: Czy = 13) {
                    zakończ
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test wrong function call argument type`() {
        assertTypeCheckingError(
            """
                czynność f(a: Liczba) {
                    zakończ
                }
                czynność g() {
                    f(fałsz)
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność h() {
                    zakończ
                }
                czynność f(a: Liczba) {
                    zakończ
                }
                czynność g() {
                    f(h())
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f(a: Czy) {
                    zakończ
                }
                czynność g() {
                    f(7)
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność h() {
                    zakończ
                }
                czynność f(a: Czy) {
                    zakończ
                }
                czynność g() {
                    f(h())
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f(a: Liczba, b: Liczba = 2, c: Liczba = 3) -> Liczba {
                    zwróć a + b + c
                }
                czynność g() {
                    f(3, c = prawda)
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność h() {
                    zakończ
                }
                czynność f(a: Liczba, b: Liczba = 2, c: Liczba = 3) -> Liczba {
                    zwróć a + b + c
                }
                czynność g() {
                    f(3, c = h())
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f(a: Czy, b: Czy = fałsz, c: Czy = fałsz) -> Czy {
                    zwróć a lub b lub c
                }
                czynność g() {
                    f(fałsz, c = 3)
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność h() {
                    zakończ
                }
                czynność f(a: Czy, b: Czy = fałsz, c: Czy = fałsz) -> Czy {
                    zwróć a lub b lub c
                }
                czynność g() {
                    f(fałsz, c = h())
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test wrong expression type in if else statements`() {
        assertTypeCheckingError(
            """
                czynność f() {
                    jeśli (4) {
                        zakończ
                    }
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    wart a: Liczba = 15
                    jeśli (a) {
                        zakończ
                    }
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność g() {
                    zakończ
                }
                czynność f() {
                    jeśli (g()) {
                        zakończ
                    }
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    jeśli (fałsz) {
                        zakończ
                    } zaś gdy (555) {
                        zakończ      
                    }
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    wart a: Liczba = 15
                    jeśli (a < 10) {
                        zakończ
                    } zaś gdy (a) {
                        zakończ      
                    }
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność g() {
                    zakończ
                }
                czynność f() {
                    jeśli (fałsz) {
                        zakończ
                    } zaś gdy (g()) {
                        zakończ      
                    }
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test wrong expression type in while loop`() {
        assertTypeCheckingError(
            """
                czynność f() {
                    zm a: Liczba = 0
                    dopóki (13) {
                        a = a + 1
                    }
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność f() {
                    zm a: Liczba = 0
                    dopóki (a) {
                        a = a + 1
                    }
                }
            """
        )
        assertTypeCheckingError(
            """
                czynność g() {
                    zakończ
                }
                czynność f() {
                    zm a: Liczba = 0
                    dopóki (g()) {
                        a = a + 1
                    }
                }
            """
        )
    }
}
