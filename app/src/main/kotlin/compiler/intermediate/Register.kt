package compiler.intermediate

// A class representing virtual cpu register
class Register(private val asmName: String = "VIRTUAL REGISTER") {
    override fun toString() = asmName

    companion object {
        val RAX = Register("rax")
        val RBX = Register("rbx")
        val RCX = Register("rcx")
        val RDX = Register("rdx")
        val RSI = Register("rsi")
        val RDI = Register("rdi")
        val RBP = Register("rbp")
        val RSP = Register("rsp")
        val R8 = Register("r8")
        val R9 = Register("r9")
        val R10 = Register("r10")
        val R11 = Register("r11")
        val R12 = Register("r12")
        val R13 = Register("r13")
        val R14 = Register("r14")
        val R15 = Register("r15")
    }
}
