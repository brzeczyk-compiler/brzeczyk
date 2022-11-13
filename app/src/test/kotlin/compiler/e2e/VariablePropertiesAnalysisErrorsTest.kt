package compiler.e2e

import compiler.common.diagnostics.Diagnostic
import compiler.e2e.E2eAsserter.assertErrorOfType
import org.junit.Ignore
import org.junit.Test

class VariablePropertiesAnalysisErrorsTest {
    private fun assertAssignmentToParameterError(program: String) {
        assertErrorOfType(program, Diagnostic.VariablePropertiesError.AssignmentToFunctionParameter::class)
    }

    private fun assertAssignmentToOuterVariableError(program: String) {
        assertErrorOfType(program, Diagnostic.VariablePropertiesError.AssignmentToOuterVariable::class)
    }

    @Ignore
    @Test
    fun `test assignment to function parameter`() {
        assertAssignmentToParameterError(
            """
                czynność f(a: Liczba) {
                    a = 10
                }
            """
        )
        assertAssignmentToParameterError(
            """
                czynność f(a: Liczba) {
                    czynność g() {
                        a = 13
                    }
                }
            """
        )
        assertAssignmentToParameterError(
            """
                czynność f() {
                    czynność g(a: Czy) {
                        a = fałsz
                    }
                }
            """
        )
    }

    @Ignore
    @Test
    fun `test assignment to outer function variable`() {
        assertAssignmentToOuterVariableError(
            """
                czynność f() {
                    zm a: Liczba = 3
                    czynność g() {
                        a = 7
                    }
                }
            """
        )
        assertAssignmentToOuterVariableError(
            """
                czynność f() {
                    zm a: Liczba = 3
                    czynność g() {
                        czynność h() {
                            a = 5
                        }
                    }
                }
            """
        )
        assertAssignmentToOuterVariableError(
            """
                czynność f() {
                    zm a: Liczba = 3
                    czynność g() {
                        czynność h() {
                            a = 5
                        }
                    }
                }
            """
        )
    }
}
