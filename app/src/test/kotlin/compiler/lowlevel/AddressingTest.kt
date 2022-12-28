package compiler.lowlevel
import compiler.intermediate.Register
import compiler.intermediate.Register.Companion.RAX
import compiler.intermediate.Register.Companion.RBX
import kotlin.test.Test
import kotlin.test.assertEquals

class AddressingTest {

    private val virtualReg1 = Register()
    private val virtualReg2 = Register()

    @Test
    fun `test displacement`() {
        val displacementConst = Addressing.Displacement(Addressing.MemoryAddress.Const(17u))
        val displacementLabel = Addressing.Displacement(Addressing.MemoryAddress.Label("label"))

        assertEquals("[17]", displacementConst.toAsm(mapOf()))
        assertEquals("[label]", displacementLabel.toAsm(mapOf()))
    }

    @Test
    fun `test base`() {
        val registersMap = mapOf(virtualReg1 to RAX)

        val baseNoDisplacement = Addressing.Base(virtualReg1)
        val baseDisplacementConst = Addressing.Base(virtualReg1, Addressing.MemoryAddress.Const(17u))
        val baseDisplacementLabel = Addressing.Base(virtualReg1, Addressing.MemoryAddress.Label("label"))

        assertEquals("[rax]", baseNoDisplacement.toAsm(registersMap))
        assertEquals("[rax + 17]", baseDisplacementConst.toAsm(registersMap))
        assertEquals("[rax + label]", baseDisplacementLabel.toAsm(registersMap))
    }

    @Test
    fun `test base and index`() {
        val registersMap = mapOf(virtualReg1 to RAX, virtualReg2 to RBX)

        val baseAndIndexNoDisplacementNoScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 1u)
        val baseAndIndexDisplacementConstNoScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 1u, Addressing.MemoryAddress.Const(17u))
        val baseAndIndexDisplacementLabelNoScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 1u, Addressing.MemoryAddress.Label("label"))
        val baseAndIndexNoDisplacementScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 4u)
        val baseAndIndexDisplacementConstScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 4u, Addressing.MemoryAddress.Const(17u))
        val baseAndIndexDisplacementLabelScale = Addressing.BaseAndIndex(virtualReg1, virtualReg2, 4u, Addressing.MemoryAddress.Label("label"))

        assertEquals("[rax + rbx]", baseAndIndexNoDisplacementNoScale.toAsm(registersMap))
        assertEquals("[rax + rbx + 17]", baseAndIndexDisplacementConstNoScale.toAsm(registersMap))
        assertEquals("[rax + rbx + label]", baseAndIndexDisplacementLabelNoScale.toAsm(registersMap))
        assertEquals("[rax + (rbx * 4)]", baseAndIndexNoDisplacementScale.toAsm(registersMap))
        assertEquals("[rax + (rbx * 4) + 17]", baseAndIndexDisplacementConstScale.toAsm(registersMap))
        assertEquals("[rax + (rbx * 4) + label]", baseAndIndexDisplacementLabelScale.toAsm(registersMap))
    }

    @Test
    fun `test index and displacement`() {
        val registersMap = mapOf(virtualReg1 to RAX)

        val indexNoDisplacement = Addressing.IndexAndDisplacement(virtualReg1, 4u)
        val indexDisplacementConst = Addressing.IndexAndDisplacement(virtualReg1, 4u, Addressing.MemoryAddress.Const(17u))
        val indexDisplacementLabel = Addressing.IndexAndDisplacement(virtualReg1, 4u, Addressing.MemoryAddress.Label("label"))

        assertEquals("[(rax * 4)]", indexNoDisplacement.toAsm(registersMap))
        assertEquals("[(rax * 4) + 17]", indexDisplacementConst.toAsm(registersMap))
        assertEquals("[(rax * 4) + label]", indexDisplacementLabel.toAsm(registersMap))
    }
}
