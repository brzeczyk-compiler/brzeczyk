package compiler.e2e

import compiler.diagnostics.Diagnostic
import compiler.e2e.E2eTestUtils.assertErrorOfType
import compiler.e2e.E2eTestUtils.assertProgramCorrect
import kotlin.test.Test

class ArgumentResolverE2eTest {
    private fun assertMissingArgumentError(program: String) {
        assertErrorOfType(program, Diagnostic.ResolutionDiagnostic.ArgumentResolutionError.MissingArgument::class)
    }

    private fun assertRepeatedArgumentError(program: String) {
        assertErrorOfType(program, Diagnostic.ResolutionDiagnostic.ArgumentResolutionError.RepeatedArgument::class)
    }

    private fun assertUnknownArgumentError(program: String) {
        assertErrorOfType(program, Diagnostic.ResolutionDiagnostic.ArgumentResolutionError.UnknownArgument::class)
    }

    private fun assertTooManyArgumentsError(program: String) {
        assertErrorOfType(program, Diagnostic.ResolutionDiagnostic.ArgumentResolutionError.TooManyArguments::class)
    }

    @Test
    fun `test missing arguments`() {
        assertMissingArgumentError(
            """
                czynność f(a: Liczba) {}
                czynność g() {
                    f()
                }
            """
        )
        assertMissingArgumentError(
            """
                czynność f(a: Czy, b: Czy) {}
                czynność g() {
                    f()
                }
            """
        )
        assertMissingArgumentError(
            """
                czynność f(a: Czy, b: Czy) {}
                czynność g() {
                    f(fałsz)
                }
            """
        )
        assertMissingArgumentError(
            """
                czynność f(a: Czy, b: Czy) {}
                czynność g() {
                    f(a = prawda)
                }
            """
        )
        assertMissingArgumentError(
            """
                czynność f(a: Czy, b: Czy) {}
                czynność g() {
                    f(b = fałsz)
                }
            """
        )
        assertMissingArgumentError(
            """
                czynność f(a: Liczba) {
                    f()
                }
            """
        )
        assertMissingArgumentError(
            """
                czynność f() {
                    czynność g(a: Liczba) { }
                    g()
                }
            """
        )
        assertMissingArgumentError(
            """
                czynność f() {
                    czynność g(a: Liczba) { }
                    czynność h() {
                        g()
                    }
                }
            """
        )
        assertMissingArgumentError(
            """
                czynność g(a: Liczba) -> Liczba {
                    zwróć a + 1 
                }
                czynność f() {
                    zm b: Liczba = g(3 + g())
                }
            """
        )
        assertMissingArgumentError(
            """
                czynność f(a: Liczba, b: Liczba, c: Liczba) { }
                czynność g() {
                    f(a=1, c=7)
                }
            """
        )
        assertProgramCorrect(
            """
                czynność główna() {}
                czynność f(a: Liczba, b: Liczba, c: Liczba) { }
                czynność g() {
                    f(c=6, b=1, a=3)
                }
            """
        )
    }

    @Test
    fun `test repeated arguments`() {
        assertRepeatedArgumentError(
            """
                czynność f(a: Liczba, b: Liczba, c: Liczba) { }
                czynność g() {
                    f(4, a=5)
                }
            """
        )
        assertRepeatedArgumentError(
            """
                czynność f(a: Liczba, b: Liczba = 0, c: Liczba = 1) { }
                czynność g() {
                    f(1, 2, b=13)
                }
            """
        )
        assertRepeatedArgumentError(
            """
                czynność f(a: Liczba, b: Liczba = 0, c: Liczba = 1) { }
                czynność g() {
                    f(a=10, b=13, a=34)
                }
            """
        )
        assertRepeatedArgumentError(
            """
                czynność f(a: Liczba = -1, b: Liczba = 0, c: Liczba = 1) { }
                czynność g() {
                    f(c=3, c=1)
                }
            """
        )
    }

    @Test
    fun `test unknown named arguments`() {
        assertUnknownArgumentError(
            """
                czynność f(a: Liczba = -1, b: Liczba = 0, c: Liczba = 1) { }
                czynność g() {
                    f(4, 4, d=123)
                }
            """
        )
        assertUnknownArgumentError(
            """
                czynność f(a: Liczba = -1, b: Liczba = 0, c: Liczba = 1) { }
                czynność g() {
                    f(x=1)
                }
            """
        )
        assertUnknownArgumentError(
            """
                czynność f(a: Liczba = -1, b: Liczba = 0, c: Liczba = 1) { }
                czynność g() {
                    f(a = 0, b = 1, d = 3)
                }
            """
        )
    }

    @Test
    fun `test too many arguments`() {
        assertTooManyArgumentsError(
            """
                czynność f() {}
                czynność g() {
                    f(1)
                }
            """
        )
        assertTooManyArgumentsError(
            """
                czynność f() {}
                czynność g() {
                    f(a=13)
                }
            """
        )
        assertTooManyArgumentsError(
            """
                czynność f(a: Liczba) {}
                czynność g() {
                    f(13, 12)
                }
            """
        )
        assertTooManyArgumentsError(
            """
                czynność f(a: Liczba, b: Czy) {}
                czynność g() {
                    f(13, fałsz, -1)
                }
            """
        )
        assertTooManyArgumentsError(
            """
                czynność g() {
                    czynność f(a: Liczba, b: Czy) {}
                    f(13, fałsz, prawda)
                }
            """
        )
    }
}
