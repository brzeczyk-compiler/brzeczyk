package compiler.intermediate_form

sealed class IntermediateFormTreeNode {
    data class MemoryRead(val register: Register, val address: Addressing) // mov reg, [addr]
    data class MemoryWrite(val address: MemoryAddress, val register: Register) // mov [addr], reg
    data class MemoryWriteConst(val address: MemoryAddress, val value: Long)

    // no logical and, or, iff and xor, since they have a short circuit semantic
    data class LogicalNegation(val resultRegister: Register)

    // arithmetic operations work on 32 bit registers
    data class Add(val resultRegister: Register, val secondRegister: Register) // add reg1, reg2
    data class AddConst(val resultRegister: Register, val value: Int) // add reg, 42
    data class AddMem(val resultRegister: Register, val address: Addressing) // add reg, [addr]

    data class Subtract(val resultRegister: Register, val secondRegister: Register)
    data class SubtractConst(val resultRegister: Register, val value: Int)
    data class SubtractMem(val resultRegister: Register, val address: Addressing)

    data class Multiply(val resultRegister: Register, val secondRegister: Register)
    data class MultiplyConst(val resultRegister: Register, val value: Int)
    data class MultiplyMem(val resultRegister: Register, val address: Addressing)

    data class Divide(val resultRegister: Register, val secondRegister: Register)
    data class DivideConst(val resultRegister: Register, val value: Int)
    data class DivideMem(val resultRegister: Register, val address: Addressing)

    data class Modulo(val resultRegister: Register, val secondRegister: Register)
    data class ModuloConst(val resultRegister: Register, val value: Int)
    data class ModuloMem(val resultRegister: Register, val address: Addressing)

    // bitwise operations also work on 32 bit registers
    data class BitAnd(val resultRegister: Register, val secondRegister: Register)
    data class BitAndConst(val resultRegister: Register, val value: Int)
    data class BitAndMem(val resultRegister: Register, val address: Addressing)

    data class BitOr(val resultRegister: Register, val secondRegister: Register)
    data class BitOrConst(val resultRegister: Register, val value: Int)
    data class BitOrMem(val resultRegister: Register, val address: Addressing)

    data class BitXor(val resultRegister: Register, val secondRegister: Register)
    data class BitXorConst(val resultRegister: Register, val value: Int)
    data class BitXorMem(val resultRegister: Register, val address: Addressing)

    data class BitShiftLeft(val resultRegister: Register, val secondRegister: Register)
    data class BitShiftLeftConst(val resultRegister: Register, val value: Int)

    data class BitShiftRight(val resultRegister: Register, val secondRegister: Register)
    data class BitShiftRightConst(val resultRegister: Register, val value: Int)

    data class BitNegation(val resultRegister: Register)

    data class Equals(val resultRegister: Register, val secondRegister: Register) // reg1 = (1 if reg1 == reg2, 0 otherwise)
    data class EqualsConst(val resultRegister: Register, val value: Int)
    data class EqualsMem(val resultRegister: Register, val address: Addressing)

    data class NotEquals(val resultRegister: Register, val secondRegister: Register)
    data class NotEqualsConst(val resultRegister: Register, val value: Int)
    data class NotEqualsMem(val resultRegister: Register, val address: Addressing)

    data class LessThan(val resultRegister: Register, val secondRegister: Register)
    data class LessThanConst(val resultRegister: Register, val value: Int)
    data class LessThanMem(val resultRegister: Register, val address: Addressing)

    data class LessThanOrEquals(val resultRegister: Register, val secondRegister: Register)
    data class LessThanOrEqualsConst(val resultRegister: Register, val value: Int)
    data class LessThanOrEqualsMem(val resultRegister: Register, val address: Addressing)

    data class GreaterThan(val resultRegister: Register, val secondRegister: Register)
    data class GreaterThanConst(val resultRegister: Register, val value: Int)
    data class GreaterThanMem(val resultRegister: Register, val address: Addressing)

    data class GreaterThanOrEquals(val resultRegister: Register, val secondRegister: Register)
    data class GreaterThanOrEqualsConst(val resultRegister: Register, val value: Int)
    data class GreaterThanOrEqualsMem(val resultRegister: Register, val address: Addressing)

    // stack operations, work on 64 bit registers
    data class StackPush(val register: Register)
    data class StackPop(val resultRegister: Register)
}
