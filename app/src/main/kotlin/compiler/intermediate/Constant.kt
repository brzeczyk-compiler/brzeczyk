package compiler.intermediate

interface Constant {
    val value: Long
}

data class FixedConstant(override val value: Long) : Constant

data class ConstantPlaceholder(var settledValue: Long? = null) : Constant {
    override val value get() = settledValue!!
}

class SummedConstant(private val first: Constant, private val second: Constant) : Constant {
    constructor(first: Long, second: Constant) : this(FixedConstant(first), second)

    override val value get() = first.value + second.value
}

class AlignedConstant(private val rawValue: Constant, private val modulo: Long, private val remainder: Long = 0L) : Constant {
    // smallest number greater or equal to rawValue having a given remainder modulo
    override val value: Long get() {
        val toAdd = remainder.mod(modulo) - rawValue.value % modulo
        return if (toAdd < 0) rawValue.value + toAdd + modulo
        else rawValue.value + toAdd
    }
}
