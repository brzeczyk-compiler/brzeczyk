package compiler.semantic_analysis
import compiler.common.diagnostics.Diagnostic
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun assertResolutionErrorsEquals(expected: List<Diagnostic>, actual: List<Diagnostic>) {
    assertEquals(expected.size, actual.size)
    (expected zip actual).forEach { assertEquals(it.first::class, it.second::class) }

    val expectedAssociatedObjects: List<List<Diagnostic.ResolutionError.ObjectAssociatedToError>> =
        expected.map { (it as Diagnostic.ResolutionError).associatedObjects }

    actual.forEach { assertTrue(it is Diagnostic.ResolutionError, "A diagnostic is of a wrong type.") }
    val actualAssociatedObjects: List<List<Diagnostic.ResolutionError.ObjectAssociatedToError>> =
        actual.map { (it as Diagnostic.ResolutionError).associatedObjects }

    assertEquals(expectedAssociatedObjects, actualAssociatedObjects)
}
