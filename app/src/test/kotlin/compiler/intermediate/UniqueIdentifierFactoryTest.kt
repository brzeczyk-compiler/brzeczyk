package compiler.intermediate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UniqueIdentifierFactoryTest {
    private val pref = UniqueIdentifierFactory.functionPrefix
    private val sep = UniqueIdentifierFactory.levelSeparator
    private val pol = UniqueIdentifierFactory.polishSignSymbol

    @Test
    fun `test basic`() {
        val factory = UniqueIdentifierFactory()
        val curr = "no_polish_signs"
        val identifier = factory.build(null, curr)
        val expected = "${pref}${sep}$curr"

        assertEquals(expected, identifier.value)
    }

    @Test
    fun `test no polish signs in output`() {
        // nasm lexer is incapable of parsing unicode
        val factory = UniqueIdentifierFactory()
        val curr = "polskie_znaki_są_żeś_zaiście_świetneż"
        val identifier = factory.build(null, curr)
        val expected = "${pref}${sep}polskie_znaki_sa#_z#es#_zais#cie_s#wietnez#"

        assertEquals(expected, identifier.value)
    }

    @Test
    fun `test prefix`() {
        val factory = UniqueIdentifierFactory()
        val curr = "no_polish_signs"
        val prefix = "some_prefix"
        val identifier = factory.build(prefix, curr)
        val expected = "${prefix}${sep}$curr"

        assertEquals(expected, identifier.value)
    }

    @Test
    fun `test identical strings with accuracy to polish signs do not cause conflicts`() {
        val factory = UniqueIdentifierFactory()
        val curr1 = "żeś"
        val curr2 = "żes"
        val curr3 = "źes"
        val curr4 = "zes"

        val commonPrefix = "some_prefix"
        val identifier1 = factory.build(commonPrefix, curr1)
        val identifier2 = factory.build(commonPrefix, curr2)
        val identifier3 = factory.build(commonPrefix, curr3)
        val identifier4 = factory.build(commonPrefix, curr4)
        val expected1 = "${commonPrefix}${sep}z#es#"
        val expected2 = "${commonPrefix}${sep}z#es"
        val expected3 = "${commonPrefix}${sep}x#es"
        val expected4 = "${commonPrefix}${sep}zes"

        assertEquals(expected1, identifier1.value)
        assertEquals(expected2, identifier2.value)
        assertEquals(expected3, identifier3.value)
        assertEquals(expected4, identifier4.value)
    }

    @Test
    fun `test illegal character throws an exception`() {
        val factory = UniqueIdentifierFactory()
        val curr = "mwahahaha|!-"
        assertFailsWith(IllegalCharacter::class) { factory.build(null, curr) }
    }

    @Test
    fun `test conflict throws an exception`() {
        val factory = UniqueIdentifierFactory()
        val first = "żeś"
        val second = "żes#" // not possible with our regex for identifier token

        factory.build(null, first)
        assertFailsWith(InconsistentFunctionNamingConvention::class) { factory.build(null, second) }
    }
}
