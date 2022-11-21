package compiler.e2e

import compiler.common.diagnostics.Diagnostic
import compiler.e2e.common.E2eAsserter.assertErrorOfType
import org.junit.Test

class VariablePropertiesAnalysisErrorsTest {
    private fun assertAssignmentToParameterError(program: String) {
        assertErrorOfType(program, Diagnostic.VariablePropertiesError.AssignmentToFunctionParameter::class)
    }

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
}
