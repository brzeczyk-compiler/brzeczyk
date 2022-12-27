package compiler.lowlevel
import compiler.intermediate.Register
import kotlin.test.Test
import kotlin.test.assertEquals

class AddressingTest {

    private val virtualReg1 = Register()
    private val virtualReg2 = Register()

    private val reg1 = Register("reg1")
    private val reg2 = Register("reg2")

    @Test
    fun `test displacement`() {
        val displacementConst = Addressing.Displacement(Addressing.MemoryAddress.Const(17u))
        val displacementLabel = Addressing.Displacement(Addressing.MemoryAddress.Label("label"))

        assertEquals("[17]", displacementConst.toAsm(mapOf()))
        assertEquals("[label]", displacementLabel.toAsm(mapOf()))
    }

    @Test
    fun `test base`() {
        val registersMap = mapOf(virtualReg1 to reg1)

        val baseNoDisplacement = Addressing.Base(virtualReg1)
        val baseDisplacementConst = Addressing.Base(virtualReg1, Addressing.MemoryAddress.Const(17u))
        val baseDisplacementLabel = Addressing.Base(virtualReg1, Addressing.MemoryAddress.Label("label"))

        assertEquals("[reg1]", baseNoDisplacement.toAsm(registersMap))
        assertEquals("[reg1 + 17]", baseDisplacementConst.toAsm(registersMap))
        assertEquals("[reg1 + label]", baseDisplacementLabel.toAsm(registersMap))
    }

    @Test
    fun `test base and index`() {
        val registersMap = mapOf(virtualReg1 to reg1, virtualReg2 to reg2)

        val baseAndIndexNoDisplacementNoScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 1u)
        val baseAndIndexDisplacementConstNoScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 1u, Addressing.MemoryAddress.Const(17u))
        val baseAndIndexDisplacementLabelNoScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 1u, Addressing.MemoryAddress.Label("label"))
        val baseAndIndexNoDisplacementScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 4u)
        val baseAndIndexDisplacementConstScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 4u, Addressing.MemoryAddress.Const(17u))
        val baseAndIndexDisplacementLabelScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 4u, Addressing.MemoryAddress.Label("label"))

        assertEquals("[reg1 + reg2]", baseAndIndexNoDisplacementNoScale.toAsm(registersMap))
        assertEquals("[reg1 + reg2 + 17]", baseAndIndexDisplacementConstNoScale.toAsm(registersMap))
        assertEquals("[reg1 + reg2 + label]", baseAndIndexDisplacementLabelNoScale.toAsm(registersMap))
        assertEquals("[reg1 + (reg2 * 4)]", baseAndIndexNoDisplacementScale.toAsm(registersMap))
        assertEquals("[reg1 + (reg2 * 4) + 17]", baseAndIndexDisplacementConstScale.toAsm(registersMap))
        assertEquals("[reg1 + (reg2 * 4) + label]", baseAndIndexDisplacementLabelScale.toAsm(registersMap))
    }

    @Test
    fun `test index and displacement`() {
        val registersMap = mapOf(virtualReg1 to reg1)

        val indexNoDisplacement = Addressing.IndexAndDisplacement(virtualReg1, 4u)
        val indexDisplacementConst = Addressing.IndexAndDisplacement(virtualReg1, 4u, Addressing.MemoryAddress.Const(17u))
        val indexDisplacementLabel = Addressing.IndexAndDisplacement(virtualReg1, 4u, Addressing.MemoryAddress.Label("label"))

        assertEquals("[(reg1 * 4)]", indexNoDisplacement.toAsm(registersMap))
        assertEquals("[(reg1 * 4) + 17]", indexDisplacementConst.toAsm(registersMap))
        assertEquals("[(reg1 * 4) + label]", indexDisplacementLabel.toAsm(registersMap))
    }
}