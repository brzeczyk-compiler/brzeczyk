package compiler.lowlevel

import compiler.intermediate.Register

sealed class Addressing {
    sealed class MemoryAddress {
        data class Const(val address: ULong) : MemoryAddress() {
            override fun toString() = address.toString()
        }
        data class Label(val label: String) : MemoryAddress() {
            override fun toString() = label
        }
    }

    abstract fun toAsm(registers: Map<Register, Register>): String

    data class Displacement(
        val displacement: MemoryAddress,
    ) : Addressing() { // [displacement]
        override fun toAsm(registers: Map<Register, Register>) = "[$displacement]"
    }

    data class Base(
        val base: Register,
        val displacement: MemoryAddress = MemoryAddress.Const(0U),
    ) : Addressing() { // [base + displacement] or [base]
        override fun toAsm(registers: Map<Register, Register>) =
            if (displacement == MemoryAddress.Const(0U)) {
                "[${registers[base]}]"
            } else {
                "[${registers[base]} + $displacement]"
            }
    }

    data class BaseAndIndex(
        val base: Register,
        val index: Register,
        val scale: UByte, // either 1,2,4,8
        val displacement: MemoryAddress = MemoryAddress.Const(0U)
    ) : Addressing() { // [base + (index * scale) + displacement], [base + (index * scale)], [base + index + displacement], or [base + index]
        private val oneUByte: UByte = 1u
        override fun toAsm(registers: Map<Register, Register>) =
            if (displacement == MemoryAddress.Const(0U)) {
                if (scale == oneUByte) {
                    "[${registers[base]} + ${registers[index]}]"
                } else {
                    "[${registers[base]} + (${registers[index]} * $scale)]"
                }
            } else {
                if (scale == oneUByte) {
                    "[${registers[base]} + ${registers[index]} + $displacement]"
                } else {
                    "[${registers[base]} + (${registers[index]} * $scale) + $displacement]"
                }
            }
    }

    data class IndexAndDisplacement(
        val index: Register,
        val scale: UByte, // either 1,2,4,8
        val displacement: MemoryAddress = MemoryAddress.Const(0U)
    ) : Addressing() { // [(index*scale)] or [(index*scale) + displacement]
        override fun toAsm(registers: Map<Register, Register>) =
            if (displacement == MemoryAddress.Const(0U)) {
                "[(${registers[index]} * $scale)]"
            } else {
                "[(${registers[index]} * $scale) + $displacement]"
            }
    }
}
