package compiler.input

import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderInputTest {
    @Test fun `test gives correct characters`() {
        val text = "A\na ?ż"
        val input: Input = ReaderInput(StringReader(text))

        assertEquals('A', input.next())
        assertEquals('\n', input.next())
        assertEquals('a', input.next())
        assertEquals(' ', input.next())
        assertEquals('?', input.next())
        assertEquals('ż', input.next())
    }

    @Test fun `test supports polish diacritics`() {
        val text = "zażółć gęślą jaźń\nZAŻÓŁĆ GĘŚLĄ JAŹŃ"
        val input: Input = ReaderInput(StringReader(text))

        assertEquals('z', input.next())
        assertEquals('a', input.next())
        assertEquals('ż', input.next())
        assertEquals('ó', input.next())
        assertEquals('ł', input.next())
        assertEquals('ć', input.next())
        assertEquals(' ', input.next())
        assertEquals('g', input.next())
        assertEquals('ę', input.next())
        assertEquals('ś', input.next())
        assertEquals('l', input.next())
        assertEquals('ą', input.next())
        assertEquals(' ', input.next())
        assertEquals('j', input.next())
        assertEquals('a', input.next())
        assertEquals('ź', input.next())
        assertEquals('ń', input.next())

        assertEquals('\n', input.next())

        assertEquals('Z', input.next())
        assertEquals('A', input.next())
        assertEquals('Ż', input.next())
        assertEquals('Ó', input.next())
        assertEquals('Ł', input.next())
        assertEquals('Ć', input.next())
        assertEquals(' ', input.next())
        assertEquals('G', input.next())
        assertEquals('Ę', input.next())
        assertEquals('Ś', input.next())
        assertEquals('L', input.next())
        assertEquals('Ą', input.next())
        assertEquals(' ', input.next())
        assertEquals('J', input.next())
        assertEquals('A', input.next())
        assertEquals('Ź', input.next())
        assertEquals('Ń', input.next())
    }

    @Test fun `test indicates end of text`() {
        val text = "xyz"
        val input: Input = ReaderInput(StringReader(text))

        assertTrue(input.hasNext())
        input.next()
        assertTrue(input.hasNext())
        input.next()
        assertTrue(input.hasNext())
        input.next()
        assertFalse(input.hasNext())
    }

    @Test fun `test indicates end of empty text`() {
        val text = ""
        val input: Input = ReaderInput(StringReader(text))

        assertFalse(input.hasNext())
    }

    @Test fun `test gives correct location`() {
        val text = "abc\nxy"
        val input: Input = ReaderInput(StringReader(text))

        assertEquals(Location(1, 1), input.getLocation())
        input.next()
        assertEquals(Location(1, 2), input.getLocation())
        input.next()
        assertEquals(Location(1, 3), input.getLocation())
        input.next()
        assertEquals(Location(1, 4), input.getLocation())
        input.next()
        assertEquals(Location(2, 1), input.getLocation())
        input.next()
        assertEquals(Location(2, 2), input.getLocation())
        input.next()
        assertEquals(Location(2, 3), input.getLocation())
    }

    @Test fun `test gives correct location for empty text`() {
        val text = ""
        val input: Input = ReaderInput(StringReader(text))

        assertEquals(Location(1, 1), input.getLocation())
    }

    @Test fun `test gives correct location with many lines`() {
        val text = "a\nb\ncc\nd\n\nf\ng\nh\ni\nj\n"
        val input: Input = ReaderInput(StringReader(text))

        assertEquals(Location(1, 1), input.getLocation())
        input.next()
        assertEquals(Location(1, 2), input.getLocation())
        input.next()
        assertEquals(Location(2, 1), input.getLocation())
        input.next()
        assertEquals(Location(2, 2), input.getLocation())
        input.next()
        assertEquals(Location(3, 1), input.getLocation())
        input.next()
        assertEquals(Location(3, 2), input.getLocation())
        input.next()
        assertEquals(Location(3, 3), input.getLocation())
        input.next()
        assertEquals(Location(4, 1), input.getLocation())
        input.next()
        assertEquals(Location(4, 2), input.getLocation())
        input.next()
        assertEquals(Location(5, 1), input.getLocation())
        input.next()
        assertEquals(Location(6, 1), input.getLocation())
        input.next()
        assertEquals(Location(6, 2), input.getLocation())
        input.next()
        assertEquals(Location(7, 1), input.getLocation())
        input.next()
        assertEquals(Location(7, 2), input.getLocation())
        input.next()
        assertEquals(Location(8, 1), input.getLocation())
        input.next()
        assertEquals(Location(8, 2), input.getLocation())
        input.next()
        assertEquals(Location(9, 1), input.getLocation())
        input.next()
        assertEquals(Location(9, 2), input.getLocation())
        input.next()
        assertEquals(Location(10, 1), input.getLocation())
        input.next()
        assertEquals(Location(10, 2), input.getLocation())
        input.next()
    }

    @Test fun `test fails on illegal rewind`() {
        val text = "abcde"
        val input: Input = ReaderInput(StringReader(text))

        input.next()
        input.next()

        assertFails { input.rewind(3) }
    }

    @Test fun `test fails on illegal rewind after flush`() {
        val text = "abcdefgh"
        val input: Input = ReaderInput(StringReader(text))

        input.next()
        input.next()
        input.next()
        input.flush()
        input.next()

        assertFails { input.rewind(2) }
    }

    @Test fun `test gives correct characters after rewind`() {
        val text = "Aą\nxy"
        val input: Input = ReaderInput(StringReader(text))

        input.next()
        input.next()
        input.next()
        input.next()
        input.rewind(3)

        assertEquals('ą', input.next())
        assertEquals('\n', input.next())
        assertEquals('x', input.next())
        assertEquals('y', input.next())
    }

    @Test fun `test indicates end of text after rewind`() {
        val text = "xyzt"
        val input: Input = ReaderInput(StringReader(text))

        input.next()
        input.next()
        input.next()
        input.rewind(2)

        assertTrue(input.hasNext())
        input.next()
        assertTrue(input.hasNext())
        input.next()
        assertTrue(input.hasNext())
        input.next()
        assertFalse(input.hasNext())
    }

    @Test fun `test gives correct location after rewind`() {
        val text = "abc\nxy"
        val input: Input = ReaderInput(StringReader(text))

        input.next()
        input.next()
        input.next()
        input.next()
        input.next()
        input.rewind(3)

        assertEquals(Location(1, 3), input.getLocation())
        input.next()
        assertEquals(Location(1, 4), input.getLocation())
        input.next()
        assertEquals(Location(2, 1), input.getLocation())
        input.next()
        assertEquals(Location(2, 2), input.getLocation())
        input.next()
        assertEquals(Location(2, 3), input.getLocation())
    }

    @Test fun `test gives correct characters after flush`() {
        val text = "Aąbxy"
        val input: Input = ReaderInput(StringReader(text))

        input.next()
        input.next()
        input.flush()

        assertEquals('b', input.next())
        assertEquals('x', input.next())
        assertEquals('y', input.next())
    }

    @Test fun `test indicates end of text after flush`() {
        val text = "xyzt"
        val input: Input = ReaderInput(StringReader(text))

        input.next()
        input.next()
        input.flush()

        assertTrue(input.hasNext())
        input.next()
        assertTrue(input.hasNext())
        input.next()
        assertFalse(input.hasNext())
    }

    @Test fun `test gives correct location after flush`() {
        val text = "abc\nxy"
        val input: Input = ReaderInput(StringReader(text))

        input.next()
        input.next()
        input.flush()

        assertEquals(Location(1, 3), input.getLocation())
        input.next()
        assertEquals(Location(1, 4), input.getLocation())
        input.next()
        assertEquals(Location(2, 1), input.getLocation())
        input.next()
        assertEquals(Location(2, 2), input.getLocation())
        input.next()
        assertEquals(Location(2, 3), input.getLocation())
    }
}
