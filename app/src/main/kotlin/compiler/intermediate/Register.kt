package compiler.intermediate

// A class representing virtual cpu register
class Register {
    companion object {
        val RAX = Register()
        val RBX = Register()
        val RCX = Register()
        val RDX = Register()
        val RSI = Register()
        val RDI = Register()
        val RBP = Register()
        val RSP = Register()
        val R8 = Register()
        val R9 = Register()
        val R10 = Register()
        val R11 = Register()
        val R12 = Register()
        val R13 = Register()
        val R14 = Register()
        val R15 = Register()
    }

    class VirtualRegisterIsNotAsmable : Throwable()

    fun toAsm(): String = when (this) {
        RAX -> "rax"
        RBX -> "rbx"
        RCX -> "rcx"
        RDX -> "rdx"
        RSI -> "rsi"
        RDI -> "rdi"
        RBP -> "rbp"
        RSP -> "rsp"
        R8 -> "r8"
        R9 -> "r9"
        R10 -> "r10"
        R11 -> "r11"
        R12 -> "r12"
        R13 -> "r13"
        R14 -> "r14"
        R15 -> "r15"
        else -> throw VirtualRegisterIsNotAsmable()
    }

    fun to8bitLower(): String = when (this) {
        RAX -> "al"
        RBX -> "bl"
        RCX -> "cl"
        RDX -> "dl"
        RSI -> "sil"
        RDI -> "dil"
        RBP -> "bpl"
        RSP -> "spl"
        R8 -> "r8b"
        R9 -> "r9b"
        R10 -> "r10b"
        R11 -> "r11b"
        R12 -> "r12b"
        R13 -> "r13b"
        R14 -> "r14b"
        R15 -> "r15b"
        else -> throw VirtualRegisterIsNotAsmable()
    }
}
