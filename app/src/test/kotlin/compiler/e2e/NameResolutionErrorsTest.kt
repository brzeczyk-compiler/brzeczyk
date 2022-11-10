package compiler.e2e

import compiler.common.diagnostics.Diagnostic
import compiler.e2e.E2eAsserter.assertErrorOfType
import kotlin.test.Ignore
import kotlin.test.Test

class NameResolutionErrorsTest {

    @Ignore
    @Test
    fun `test undefined variable`() {
        assertErrorOfType(
            """
                    czynność f() {
                        x = 17
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.UndefinedVariable::class
        )
    }

    @Ignore
    @Test
    fun `test variable defined in other scope`() {
        assertErrorOfType(
            """
                    czynność f() -> Liczba {
                        {
                            zm x: Liczba = 17
                        }
                        zwróć x
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.UndefinedVariable::class
        )
    }

    @Ignore
    @Test
    fun `test variable defined in an inner function`() {
        assertErrorOfType(
            """
                    czynność f() -> Liczba {
                        czynność g() {
                            zm x: Liczba = 17
                        }
                        zwróć x
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.UndefinedVariable::class
        )
    }

    @Ignore
    @Test
    fun `test undefined function`() {
        assertErrorOfType(
            """
                    czynność f() -> Liczba {
                        zwróć g()
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.UndefinedFunction::class
        )
    }

    @Ignore
    @Test
    fun `test inner function`() {
        assertErrorOfType(
            """
                    czynność f() -> Liczba {
                        czynność g() {
                            czynność h() -> Liczba {
                                zwróć 17
                            }
                        }
                        zwróć h()
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.UndefinedFunction::class
        )
    }

    @Ignore
    @Test
    fun `test conflicts (variables)`() {
        assertErrorOfType(
            """
                    czynność f() {
                        zm x: Liczba = 17
                        zm x: Liczba = 18
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.NameConflict::class
        )
    }

    @Ignore
    @Test
    fun `test conflicts (different modifiers variables)`() {
        assertErrorOfType(
            """
                    czynność f() {
                        zm x: Liczba = 17
                        wart x: Liczba = 18
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.NameConflict::class
        )
        assertErrorOfType(
            """
                    czynność f() {
                        zm x: Liczba = 17
                        stała x: Liczba = 18
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.NameConflict::class
        )
    }

    @Ignore
    @Test
    fun `test conflicts (variables of different type)`() {
        assertErrorOfType(
            """
                    czynność f() {
                        zm x: Liczba = 17
                        zm x: Czy = prawda
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.NameConflict::class
        )
    }

    @Ignore
    @Test
    fun `test conflicts (functions)`() {
        assertErrorOfType(
            """
                    czynność f() {
                        czynność g() -> Liczba {
                            zwróć 17
                        }
                        czynność g() -> Liczba {
                            zwróć 18
                        }
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.NameConflict::class
        )
    }

    @Ignore
    @Test
    fun `test conflicts (functions with different return types)`() {
        assertErrorOfType(
            """
                    czynność f() -> Liczba {
                        czynność g() -> Czy {
                            zwróć prawda
                        }
                        czynność g() -> Nic {
                            zakończ
                        }
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.NameConflict::class
        )
    }

    @Ignore
    @Test
    fun `test conflicts (functions with different signatures)`() {
        assertErrorOfType(
            """
                    czynność f() -> Liczba {
                        czynność g(a: Liczba) { }
                        czynność g(a: Czy) { }
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.NameConflict::class
        )
    }

    // Assuming no functional features.

    @Ignore
    @Test
    fun `test calling variables`() {
        assertErrorOfType(
            """
                    czynność f() -> Czy {
                        zm x: Czy = prawda
                        zwróć x()
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.VariableIsNotCallable::class
        )
    }

    // I'm not 100% sure if the next two tests make sense
    @Ignore
    @Test
    fun `test using functions in assignment`() {
        assertErrorOfType(
            """
                    czynność f() {
                        czynność g() { }
                        zm x: Liczba = x
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.FunctionIsNotVariable::class
        )
    }

    @Ignore
    @Test
    fun `test using functions in conditions`() {
        assertErrorOfType(
            """
                    czynność f() -> Czy {
                        czynność g() { }
                        zwróć (g == 17)
                    }
                    
                """,
            Diagnostic.NameResolutionErrors.FunctionIsNotVariable::class
        )
    }
}
