package compiler.intermediate_form

// A class representing virtual cpu register
sealed class Register {
    class NormalRegister: Register()
    class ReturnValueRegister: Register()
}
