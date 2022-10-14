package compiler.lexer.input

import compiler.lexer.Location
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputImplTest {
    @Test fun readNextCharacters() {
        val text = "A\na ?ż"
        val input: Input = InputImpl(StringReader(text))

        assertEquals('A', input.next())
        assertEquals('\n', input.next())
        assertEquals('a', input.next())
        assertEquals(' ', input.next())
        assertEquals('?', input.next())
        assertEquals('ż', input.next())
    }

    @Test fun indicateEndOfText() {
        val text = "xyz"
        val input: Input = InputImpl(StringReader(text))

        assertTrue(input.hasNext())
        input.next()
        assertTrue(input.hasNext())
        input.next()
        assertTrue(input.hasNext())
        input.next()
        assertFalse(input.hasNext())
    }

    @Test fun getLocation() {
        val text = "abc\nxy"
        val input: Input = InputImpl(StringReader(text))

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

    @Test fun illegalRewind() {
        val text = "abcde"
        val input: Input = InputImpl(StringReader(text))

        input.next()
        input.next()

        assertFails { input.rewind(3) }
    }

    @Test fun readNextCharactersAfterRewind() {
        val text = "Aą\nxy"
        val input: Input = InputImpl(StringReader(text))

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

    @Test fun indicateEndOfTextAfterRewind() {
        val text = "xyzt"
        val input: Input = InputImpl(StringReader(text))

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

    @Test fun getLocationAfterRewind() {
        val text = "abc\nxy"
        val input: Input = InputImpl(StringReader(text))

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

    @Test fun readNextCharactersAfterFlush() {
        val text = "Aąbxy"
        val input: Input = InputImpl(StringReader(text))

        input.next()
        input.next()
        input.flush()

        assertEquals('b', input.next())
        assertEquals('x', input.next())
        assertEquals('y', input.next())
    }

    @Test fun indicateEndOfTextAfterFlush() {
        val text = "xyzt"
        val input: Input = InputImpl(StringReader(text))

        input.next()
        input.next()
        input.flush()

        assertTrue(input.hasNext())
        input.next()
        assertTrue(input.hasNext())
        input.next()
        assertFalse(input.hasNext())
    }

    @Test fun getLocationAfterFlush() {
        val text = "abc\nxy"
        val input: Input = InputImpl(StringReader(text))

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
