package compiler.intermediate

import kotlin.test.Test
import kotlin.test.assertEquals

class ConstantTest {
    @Test
    fun `test AlignedConstant`() {
        assertEquals(AlignedConstant(FixedConstant(23), 10, 3).value, 23)
        assertEquals(AlignedConstant(FixedConstant(23), 10, 5).value, 25)
        assertEquals(AlignedConstant(FixedConstant(23), 10, 0).value, 30)
        assertEquals(AlignedConstant(FixedConstant(23), 10, 2).value, 32)
        assertEquals(AlignedConstant(FixedConstant(23), 10, -44).value, 26)
    }
}
