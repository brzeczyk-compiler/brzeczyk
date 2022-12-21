package compiler.analysis
import compiler.diagnostics.Diagnostic
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun assertResolutionDiagnosticEquals(expected: List<Diagnostic>, actual: List<Diagnostic>) {
    assertEquals(expected.size, actual.size)
    (expected zip actual).forEach { assertEquals(it.first::class, it.second::class) }

    val expectedAssociatedObjects: List<List<Diagnostic.ResolutionDiagnostic.ObjectAssociatedToError>> =
        expected.map { (it as Diagnostic.ResolutionDiagnostic).associatedObjects }

    actual.forEach { assertTrue(it is Diagnostic.ResolutionDiagnostic, "A diagnostic is of a wrong type.") }
    val actualAssociatedObjects: List<List<Diagnostic.ResolutionDiagnostic.ObjectAssociatedToError>> =
        actual.map { (it as Diagnostic.ResolutionDiagnostic).associatedObjects }

    assertEquals(expectedAssociatedObjects, actualAssociatedObjects)
}
