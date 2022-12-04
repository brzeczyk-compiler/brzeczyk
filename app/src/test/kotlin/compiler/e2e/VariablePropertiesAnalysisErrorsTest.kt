package compiler.e2e

import compiler.common.diagnostics.Diagnostic
import compiler.e2e.common.E2eAsserter.assertErrorOfType
import kotlin.test.Ignore
import kotlin.test.Test

class VariablePropertiesAnalysisErrorsTest {
    private fun assertAssignmentToParameterError(program: String) {
        assertErrorOfType(program, Diagnostic.VariablePropertiesError.AssignmentToFunctionParameter::class)
    }

    @Ignore // FIXME: as this is an e2e test, the error is reported by the type checker, and not the variable properties analyzer
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
