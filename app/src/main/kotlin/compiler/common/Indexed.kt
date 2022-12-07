package compiler.common

interface Indexed<T, K> {
    operator fun get(a: T): K
}
