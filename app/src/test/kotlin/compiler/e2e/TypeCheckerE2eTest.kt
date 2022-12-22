package compiler.e2e

import compiler.diagnostics.Diagnostic
import compiler.e2e.E2eTestUtils.assertErrorOfType
import compiler.e2e.E2eTestUtils.assertProgramCorrect
import kotlin.test.Test

class TypeCheckerE2eTest {
    private fun assertInvalidTypeError(program: String) {
        assertErrorOfType(program, Diagnostic.ResolutionDiagnostic.TypeCheckingError.InvalidType::class)
    }

    private fun assertConditionalMismatchError(program: String) {
        assertErrorOfType(program, Diagnostic.ResolutionDiagnostic.TypeCheckingError.ConditionalTypesMismatch::class)
    }

    private fun assertUninitializedGlobalVariableError(program: String) {
        assertErrorOfType(program, Diagnostic.ResolutionDiagnostic.TypeCheckingError.UninitializedGlobalVariable::class)
    }

    private fun assertConstantWithoutValueError(program: String) {
        assertErrorOfType(program, Diagnostic.ResolutionDiagnostic.TypeCheckingError.ConstantWithoutValue::class)
    }

    private fun assertNonConstantExpressionError(program: String) {
        assertErrorOfType(program, Diagnostic.ResolutionDiagnostic.TypeCheckingError.NonConstantExpression::class)
    }

    private fun assertMissingReturnStatementError(program: String) {
        assertErrorOfType(program, Diagnostic.ResolutionDiagnostic.TypeCheckingError.MissingReturnStatement::class)
    }

    @Test
    fun `test instantiate variable with wrong type`() {
        assertInvalidTypeError("zm a: Liczba = prawda;")
        assertInvalidTypeError("zm a: Liczba = fałsz;")
        assertInvalidTypeError("czynność test() { zm a: Liczba = (5 > 3) lub 0 == -1; }")
        assertInvalidTypeError("czynność test() { zm b: Czy = fałsz; zm a: Liczba = b; }")
        assertInvalidTypeError(
            """
                czynność f() -> Czy {
                    zwróć prawda
                }
                czynność g() {
                    zm a: Liczba = f()
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    zm a: Liczba = f()
                }
            """
        )

        assertInvalidTypeError("zm a: Czy = 0;")
        assertInvalidTypeError("zm a: Czy = 13234;")
        assertInvalidTypeError("czynność test() { zm a: Czy = 5 * 3 - (1 % 2); }")
        assertInvalidTypeError("czynność test() { zm b: Liczba = 4; zm a: Czy = b; }")
        assertInvalidTypeError(
            """
                czynność f() -> Liczba {
                    zwróć 3
                }
                czynność g() {
                    zm a: Czy = f()
                }
            """
        )
        assertInvalidTypeError(
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

    @Test
    fun `test uninitialized global variables`() {
        assertUninitializedGlobalVariableError("zm a: Liczba;")
        assertUninitializedGlobalVariableError("zm a: Czy;")
        assertUninitializedGlobalVariableError("wart a: Liczba;")
        assertUninitializedGlobalVariableError("wart a: Czy;")
    }

    @Test
    fun `test constant without value`() {
        assertConstantWithoutValueError("stała a: Liczba;")
        assertConstantWithoutValueError("stała a: Czy;")
        assertConstantWithoutValueError(
            """
                czynność f() {
                    stała a: Liczba
                }
            """
        )
        assertConstantWithoutValueError(
            """
                czynność f() {
                    stała a: Czy
                }
            """
        )
    }

    @Test
    fun `test compile time constants`() {
        // The commented out lines don't work right now as only
        // integer and boolean literals are recognized as constant expressions.
        // We might want to evaluate constant expressions as right now
        // we don't even support negative numbers

        assertNonConstantExpressionError(
            """
                czynność f() {
                    zm a: Liczba = 3
                    stała b: Liczba = a
                }
            """
        )
        assertNonConstantExpressionError(
            """
                czynność f() {
                    zm a: Liczba = 3
                    stała b: Liczba = a * 2
                }
            """
        )
        assertNonConstantExpressionError(
            """
                czynność f(a: Liczba) {
                    stała b: Liczba = a
                }
            """
        )
        assertNonConstantExpressionError(
            """
                czynność f(a: Liczba) {a
                    stała b: Czy = a > 7 albo nie a < 3
                }
            """
        )
        assertNonConstantExpressionError(
            """
                czynność f() {
                    zm a: Czy = fałsz
                    stała b: Liczba = a ? 13 : 10
                }
            """
        )

        assertProgramCorrect("stała a: Czy = prawda;")
        assertProgramCorrect("stała a: Czy = fałsz;")
//        assertProgramCorrect("stała a: Czy = 134 > 43;")
//        assertProgramCorrect("stała a: Czy = 34 > 7 ? fałsz : 23 == 10;")
        assertProgramCorrect("stała a: Liczba = 0;")
        assertProgramCorrect("stała a: Liczba = 123;")
//        assertProgramCorrect("stała a: Liczba = -17;")
//        assertProgramCorrect("stała a: Liczba = 10 + 4;")
//        assertProgramCorrect("stała a: Liczba = 1 << 8;")
//        assertProgramCorrect("stała a: Liczba = 4 > 6 ? -3 : 30 % 7;")
//        assertProgramCorrect(
//            """
//                stała a: Liczba = 40
//                stała b: Liczba = a
//            """
//        )
//        assertProgramCorrect(
//            """
//                stała a: Czy = prawda
//                stała b: Liczba = a ? 1 : 2
//            """
//        )
//        assertProgramCorrect(
//            """
//                stała b: Liczba = 14
//                stała a: Liczba = b >> 1
//                czynność f() {
//                    stała b: Liczba = a > 10 ? a + 1 : a * 17
//                }
//            """
//        )
    }

    @Test
    fun `test non constant default function parameters`() {
        // The commented out lines don't work right now as only
        // integer and boolean literals are recognized as constant expressions.
        // We might want to evaluate constant expressions as right now
        // we don't even support negative numbers

        assertNonConstantExpressionError(
            """
                zm a: Liczba = 143
                czynność g(b: Liczba = a + 1) {
                    zakończ
                }
            """
        )
        assertNonConstantExpressionError(
            """
                zm a: Czy = fałsz
                czynność f(b: Czy = nie a) {
                    zakończ
                }
            """
        )

        assertProgramCorrect(
            """
                czynność f(a: Liczba = 13) { 
                    zakończ
                }
            """
        )
        assertProgramCorrect(
            """
                czynność f(a: Czy = fałsz) { 
                    zakończ
                }
            """
        )
//        assertProgramCorrect(
//            """
//                czynnośc f(b: Liczba = 35 / 8 - 1) {
//                    zakończ
//                }
//            """
//        )
//        assertProgramCorrect(
//            """
//                stała a: Liczba = 124
//                czynnośc f(b: Liczba = a) {
//                    zakończ
//                }
//            """
//        )
//        assertProgramCorrect(
//            """
//                stała a: Liczba = 124
//                czynnośc f(b: Liczba = 3 * a + 1) {
//                    zakończ
//                }
//            """
//        )
//        assertProgramCorrect(
//            """
//                stała a: Czy = prawda
//                czynnośc f(b: Liczba = nie a lub fałsz) {
//                    zakończ
//                }
//            """
//        )
//        assertProgramCorrect(
//            """
//                czynnośc f() {
//                    stała a: Czy = prawda
//                    czynność g(b: Liczba = a ? 3 : 4) {
//                        zakończ
//                    }
//                }
//            """
//        )
    }

    @Test
    fun `test assignment to variable of wrong type`() {
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Liczba = 1 
                    a = prawda
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Liczba = 1
                    a = fałsz
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Liczba = 1
                    a = (5 > 3) lub 0 == -1
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm b: Czy = fałsz
                    zm a: Liczba
                    a = b
                }
            """
        )
        assertInvalidTypeError(
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
        assertInvalidTypeError(
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

        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Czy
                    a = 0
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Czy
                    a = -13
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Czy
                    a = 5 * 3 - (1 % 2)
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm b: Liczba = 4; 
                    zm a: Czy
                    a = b
                }
            """
        )
        assertInvalidTypeError(
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
        assertInvalidTypeError(
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

    @Test
    fun `test integer operators with wrong types`() {
        val binaryOperators = listOf("+", "-", "*", "/", "%", "^", "&", "|", "<<", ">>")
        val unaryOperators = listOf("-", "~")

        for (operator in binaryOperators) {
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Liczba = a $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Liczba = b $operator a;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm b: Czy = prawda
                        zm c: Liczba = f() $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm a: Liczba = 4
                        zm c: Liczba = a $operator f();
                    }
                """
            )
        }

        for (operator in unaryOperators) {
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm b: Czy = prawda
                        zm c: Liczba = $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm c: Liczba = $operator f();
                    }
                """
            )
        }
    }

    @Test
    fun `test boolean operators with wrong types`() {
        val binaryOperators = listOf("oraz", "lub", "wtw", "albo")
        val unaryOperators = listOf("nie")

        for (operator in binaryOperators) {
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Czy = a $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Czy = b $operator a;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm b: Czy = prawda
                        zm c: Czy = f() $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm a: Liczba = 4
                        zm c: Czy = a $operator f();
                    }
                """
            )
        }

        for (operator in unaryOperators) {
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm b: Liczba = 17
                        zm c: Czy = $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm c: Czy = $operator f();
                    }
                """
            )
        }
    }

    @Test
    fun `test comparison operators with wrong types`() {
        val comparisonOperators = listOf("==", "!=", ">", ">=", "<", "<=")

        for (operator in comparisonOperators) {
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Czy = a $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność test() {
                        zm a: Liczba = 4
                        zm b: Czy = prawda
                        zm c: Czy = b $operator a;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm b: Czy = prawda
                        zm c: Czy = f() $operator b;
                    }
                """
            )
            assertInvalidTypeError(
                """
                    czynność f() {
                        zakończ 
                    }
                    czynność test() {
                        zm a: Liczba = 4
                        zm c: Czy = a $operator f();
                    }
                """
            )
        }
    }

    @Test
    fun `test conditional operator with wrong types`() {
        assertConditionalMismatchError("czynność test() { zm a: Liczba = prawda ? 10 : fałsz; }")
        assertConditionalMismatchError("czynność test() { zm a: Liczba = prawda ? prawda : 13; }")
        assertConditionalMismatchError("czynność test() { zm a: Liczba = 34 ? fałsz : 14; }")
        assertInvalidTypeError("czynność test() { zm a: Liczba = 34 ? 10 : 31; }")
        assertInvalidTypeError("czynność test() { zm a: Liczba = 99 ? prawda : fałsz; }")
        assertInvalidTypeError("czynność test() { zm a: Czy = fałsz ? 11 : 16; }")
        assertInvalidTypeError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    f() ? 10 : 11                            
                }
            """
        )
        assertConditionalMismatchError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    prawda ? f() : 11                            
                }
            """
        )
        assertConditionalMismatchError(
            """
                czynność f() {
                    zakończ
                }
                czynność g() {
                    prawda ? 12 : f()                   
                }
            """
        )
        assertConditionalMismatchError(
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

    @Test
    fun `test wrong return type`() {
        assertInvalidTypeError(
            """
                czynność f() {
                    zwróć 4                            
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zwróć fałsz                            
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() -> Liczba {
                    zakończ                            
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() -> Liczba {
                    zwróć fałsz                            
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() -> Czy {
                    zakończ                            
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() -> Czy {
                    zwróć 654
                }
            """
        )
    }

    @Test
    fun `test wrong default parameter type`() {
        assertInvalidTypeError(
            """
                czynność f(a: Liczba = fałsz) {
                    zakończ
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f(a: Czy = 13) {
                    zakończ
                }
            """
        )
    }

    @Test
    fun `test wrong function call argument type`() {
        assertInvalidTypeError(
            """
                czynność f(a: Liczba) {
                    zakończ
                }
                czynność g() {
                    f(fałsz)
                }
            """
        )
        assertInvalidTypeError(
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
        assertInvalidTypeError(
            """
                czynność f(a: Czy) {
                    zakończ
                }
                czynność g() {
                    f(7)
                }
            """
        )
        assertInvalidTypeError(
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
        assertInvalidTypeError(
            """
                czynność f(a: Liczba, b: Liczba = 2, c: Liczba = 3) -> Liczba {
                    zwróć a + b + c
                }
                czynność g() {
                    f(3, c = prawda)
                }
            """
        )
        assertInvalidTypeError(
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
        assertInvalidTypeError(
            """
                czynność f(a: Czy, b: Czy = fałsz, c: Czy = fałsz) -> Czy {
                    zwróć a lub b lub c
                }
                czynność g() {
                    f(fałsz, c = 3)
                }
            """
        )
        assertInvalidTypeError(
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

    @Test
    fun `test wrong expression type in if else statements`() {
        assertInvalidTypeError(
            """
                czynność f() {
                    jeśli (4) {
                        zakończ
                    }
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    wart a: Liczba = 15
                    jeśli (a) {
                        zakończ
                    }
                }
            """
        )
        assertInvalidTypeError(
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
        assertInvalidTypeError(
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
        assertInvalidTypeError(
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
        assertInvalidTypeError(
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

    @Test
    fun `test wrong expression type in while loop`() {
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Liczba = 0
                    dopóki (13) {
                        a = a + 1
                    }
                }
            """
        )
        assertInvalidTypeError(
            """
                czynność f() {
                    zm a: Liczba = 0
                    dopóki (a) {
                        a = a + 1
                    }
                }
            """
        )
        assertInvalidTypeError(
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

    @Test
    fun `test non Unit function has explicit return at the end`() {
        assertProgramCorrect("czynność f() -> Nic { zakończ; }")
        assertProgramCorrect(
            """
                czynność f() -> Nic {
                    zm a: Liczba = 0
                    a + 1
                }
            """
        )
        assertProgramCorrect(
            """
                czynność f() -> Liczba {
                    zm a: Liczba = 0
                    zwróć a + 1
                }
            """
        )
        assertMissingReturnStatementError("czynność f() -> Liczba {}")
        assertMissingReturnStatementError(
            """
                czynność f() -> Liczba {
                    zm a: Liczba = 0
                    a + 1
                }
            """
        )
        assertMissingReturnStatementError(
            """
                czynność f() -> Czy {
                    jeśli (fałsz)
                        zwróć fałsz
                    zaś gdy(fałsz)
                        zwróć fałsz
                }
            """
        )
        assertProgramCorrect(
            """
                czynność f() -> Czy {
                    jeśli (prawda) {
                        jeśli (prawda) {
                            jeśli (prawda)
                                zwróć prawda
                            wpp zwróć fałsz
                        }
                        wpp zwróć fałsz
                    }
                    wpp zwróć fałsz
                }
            """
        )
        assertMissingReturnStatementError(
            """
                czynność f() -> Czy {
                    jeśli (prawda) {
                        jeśli (prawda) {
                            jeśli (prawda)
                                zwróć prawda
                            // missing wpp
                        }
                        wpp zwróć fałsz
                    }
                    wpp zwróć fałsz
                }
            """
        )
        assertProgramCorrect("czynność f() -> Czy { { { zwróć prawda;} } }")
    }
}
