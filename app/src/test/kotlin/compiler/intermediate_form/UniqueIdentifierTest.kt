package compiler.intermediate_form

import kotlin.test.Test
import kotlin.test.assertEquals
class UniqueIdentifierTest {
    @Test
    fun `test basic`() {
        val factory = UniqueIdentifierFactory()
        val curr = "no_polish_signs"
        val identifier = factory.build(null, curr)

        assertEquals(curr, identifier.value)
    }

    @Test
    fun `test no polish signs in output`() {
        // nasm lexer is incapable of parsing unicode
        val factory = UniqueIdentifierFactory()
        val curr = "polskie_znaki_są_żeś_zaiście_świetneż"
        val identifier = factory.build(null, curr)

        assertEquals("polskie_znaki_sa_zes_zaiscie_swietnez", identifier.value)
    }

    @Test
    fun `test prefix`() {
        val factory = UniqueIdentifierFactory()
        val curr = "no_polish_signs"
        val prefix = "some_prefix"
        val identifier = factory.build(prefix, curr)

        assertEquals(prefix + "|" + curr, identifier.value)
    }

    @Test
    fun `test conflicts are resolved by adding underscores at the end`() {
        val factory = UniqueIdentifierFactory()
        val curr1 = "żeś"
        val curr2 = "żes"
        val curr3 = "zes"

        val commonPrefix = "some_prefix"
        val identifier1 = factory.build(commonPrefix, curr1)
        val identifier2 = factory.build(commonPrefix, curr2)
        val identifier3 = factory.build(commonPrefix, curr3)

        assertEquals("$commonPrefix|zes", identifier1.value)
        assertEquals("$commonPrefix|zes_", identifier2.value)
        assertEquals("$commonPrefix|zes__", identifier3.value)
    }
}
