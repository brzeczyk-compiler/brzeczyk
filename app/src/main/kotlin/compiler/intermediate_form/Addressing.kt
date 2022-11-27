package compiler.intermediate_form

typealias MemoryAddress = ULong
const val memoryUnitSize: ULong = 8u

sealed class Addressing {
    data class Displacement(val displacement: MemoryAddress) : Addressing() // [displacement]
    data class Base(val base: Regex, val displacement: MemoryAddress = 0U) : Addressing() // [base + displacement] or [base]
    data class BaseAndIndex(
        val base: Regex,
        val index: Register,
        val scale: UByte, // either 1,2,4,8
        val displacement: MemoryAddress = 0U
    ) : Addressing() // [base + (index * scale) + displacement], [base + (index * scale)], [base + index + displacement], or [base + index]
    data class IndexAndDisplacement(
        val index: Register,
        val scale: UByte, // either 1,2,4,8
        val displacement: MemoryAddress = 0U
    ) : Addressing() // [(index*scale) + displacement]
}
