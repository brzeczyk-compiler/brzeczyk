package compiler.intermediate_form

sealed class Addressing {
    sealed class MemoryAddress {
        data class Const(val address: ULong) : MemoryAddress()
        data class Label(val label: String) : MemoryAddress()
    }

    data class Displacement(val displacement: MemoryAddress) : Addressing() // [displacement]
    data class Base(val base: Register, val displacement: MemoryAddress = MemoryAddress.Const(0U)) : Addressing() // [base + displacement] or [base]
    data class BaseAndIndex(
        val base: Register,
        val index: Register,
        val scale: UByte, // either 1,2,4,8
        val displacement: MemoryAddress = MemoryAddress.Const(0U)
    ) : Addressing() // [base + (index * scale) + displacement], [base + (index * scale)], [base + index + displacement], or [base + index]
    data class IndexAndDisplacement(
        val index: Register,
        val scale: UByte, // either 1,2,4,8
        val displacement: MemoryAddress = MemoryAddress.Const(0U)
    ) : Addressing() // [(index*scale) + displacement]
}
