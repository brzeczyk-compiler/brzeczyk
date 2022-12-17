package compiler.common

interface Constant {
    val value: Long
}

data class FixedConstant(override val value: Long) : Constant
