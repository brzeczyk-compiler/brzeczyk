package compiler.intermediate_form

sealed class IntermediateFormTreeNode {
    data class MemoryRead(val register: Register, val address: Addressing) : IntermediateFormTreeNode() // mov reg, [addr]
    data class MemoryWrite(val address: MemoryAddress, val register: Register) : IntermediateFormTreeNode() // mov [addr], reg
    data class MemoryWriteConst(val address: MemoryAddress, val value: Long) : IntermediateFormTreeNode()

    // no logical and, or, iff and xor, since they have a short circuit semantic
    data class LogicalNegation(val resultRegister: Register) : IntermediateFormTreeNode()

    // arithmetic operations work on 32 bit registers
    data class Add(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode() // add reg1, reg2
    data class AddConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode() // add reg, 42
    data class AddMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode() // add reg, [addr]

    data class Subtract(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class SubtractConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class SubtractMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    data class Multiply(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class MultiplyConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class MultiplyMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    data class Divide(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class DivideConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class DivideMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    data class Modulo(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class ModuloConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class ModuloMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    // bitwise operations also work on 32 bit registers
    data class BitAnd(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class BitAndConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class BitAndMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    data class BitOr(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class BitOrConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class BitOrMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    data class BitXor(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class BitXorConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class BitXorMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    data class BitShiftLeft(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class BitShiftLeftConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()

    data class BitShiftRight(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class BitShiftRightConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()

    data class BitNegation(val resultRegister: Register) : IntermediateFormTreeNode()

    data class Equals(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode() // reg1 = (1 if reg1 == reg2, 0 otherwise)
    data class EqualsConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class EqualsMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    data class NotEquals(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class NotEqualsConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class NotEqualsMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    data class LessThan(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class LessThanConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class LessThanMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    data class LessThanOrEquals(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class LessThanOrEqualsConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class LessThanOrEqualsMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    data class GreaterThan(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class GreaterThanConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class GreaterThanMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    data class GreaterThanOrEquals(val resultRegister: Register, val secondRegister: Register) : IntermediateFormTreeNode()
    data class GreaterThanOrEqualsConst(val resultRegister: Register, val value: Int) : IntermediateFormTreeNode()
    data class GreaterThanOrEqualsMem(val resultRegister: Register, val address: Addressing) : IntermediateFormTreeNode()

    // stack operations, work on 64 bit registers
    data class StackPush(val register: Register) : IntermediateFormTreeNode()
    data class StackPop(val resultRegister: Register) : IntermediateFormTreeNode()

    class NoOp : IntermediateFormTreeNode() // For testing
}
