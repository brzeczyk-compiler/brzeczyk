package compiler.intermediate_form

import compiler.ast.NamedNode
import compiler.ast.Variable
import compiler.common.intermediate_form.FunctionDetailsGeneratorInterface
import compiler.common.reference_collections.ReferenceHashMap
import java.lang.Exception

enum class VariableLocationType {
    MEMORY,
    REGISTER
}

val argPositionToRegister = listOf(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.R8, Register.R9)
val caleeSavedRegistersWithoutRSP = listOf(Register.RBX, Register.RBP, Register.R12, Register.R13, Register.R14, Register.R15)

data class FunctionDetailsGenerator(
    val parameters: List<NamedNode>,
    val variableToStoreFunctionResult: Variable?,
    val functionLocationInCode: IntermediateFormTreeNode.MemoryAddress,
    val depth: ULong,
    val variablesLocationTypes: Map<NamedNode, VariableLocationType>, // should contain parameters
    val displayAddress: IntermediateFormTreeNode,
    val createRegisterFor: (NamedNode) -> Register = { Register() } // for testing
) : FunctionDetailsGeneratorInterface {

    private val variablesStackOffsets: MutableMap<NamedNode, ULong> = ReferenceHashMap()
    private val variablesRegisters: MutableMap<NamedNode, Register> = ReferenceHashMap()
    private var variablesTotalOffset: ULong = 0u
    private val previousDisplayEntryRegister = Register()

    init {
        for ((variable, locationType) in variablesLocationTypes.entries) {
            when (locationType) {
                VariableLocationType.MEMORY -> {
                    variablesStackOffsets[variable] = variablesTotalOffset
                    variablesTotalOffset += memoryUnitSize
                }

                VariableLocationType.REGISTER -> {
                    variablesRegisters[variable] = createRegisterFor(variable)
                }
            }
        }
    }

    override fun genCall(
        args: List<IntermediateFormTreeNode>,
    ): FunctionDetailsGeneratorInterface.FunctionCallIntermediateForm {
        // First, it moves arguments to appropriate registers (or pushes to stack) according to x86 call convention.
        // Then, it adds call instruction to actually call a given function
        // At the end it creates IFTNode to get function result

        val cfgBuilder = ControlFlowGraphBuilder()

        for ((arg, register) in args zip argPositionToRegister) {
            val node = IntermediateFormTreeNode.RegisterWrite(register, arg)
            cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, node)
        }
        for (arg in args.drop(argPositionToRegister.size).reversed()) {
            val node = IntermediateFormTreeNode.StackPush(arg)
            cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, node)
        }

        cfgBuilder.addLinkFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.Call(functionLocationInCode)
        )

        var readResultNode: IFTNode? = null
        if (variableToStoreFunctionResult != null) {
            cfgBuilder.addLinkFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IntermediateFormTreeNode.RegisterWrite(Register.RAX, genRead(variableToStoreFunctionResult, true))
            )
            readResultNode = IntermediateFormTreeNode.RegisterRead(Register.RAX)
        }
        return FunctionDetailsGeneratorInterface.FunctionCallIntermediateForm(cfgBuilder.build(), readResultNode)
    }

    override fun genPrologue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // move args from Registers and Stack
        for ((param, register) in parameters zip argPositionToRegister) {
            cfgBuilder.addLinkFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                genWrite(
                    param,
                    IntermediateFormTreeNode.RegisterRead(register),
                    true
                )
            )
        }
        for (param in parameters.drop(argPositionToRegister.size)) {
            cfgBuilder.addLinkFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                genWrite(
                    param,
                    IntermediateFormTreeNode.StackPop(),
                    true
                )
            )
        }

        // backup callee-saved registers
        for (register in caleeSavedRegistersWithoutRSP.reversed())
            cfgBuilder.addLinkFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IntermediateFormTreeNode.StackPush(IntermediateFormTreeNode.RegisterRead(register))
            )

        // update rbp
        val movRbpRsp = IntermediateFormTreeNode.RegisterWrite(
            Register.RBP,
            IntermediateFormTreeNode.RegisterRead(Register.RSP)
        )
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, movRbpRsp)

        // allocate memory for local variables
        val subRsp = IntermediateFormTreeNode.RegisterWrite(
            Register.RSP,
            IntermediateFormTreeNode.Subtract(
                IntermediateFormTreeNode.RegisterRead(Register.RSP),
                IntermediateFormTreeNode.Const(variablesTotalOffset.toLong())
            )
        )
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, subRsp)

        // update display
        val savePreviousRbp = IntermediateFormTreeNode.RegisterWrite(
            previousDisplayEntryRegister,
            IntermediateFormTreeNode.MemoryRead(
                IntermediateFormTreeNode.Subtract(
                    displayAddress,
                    IntermediateFormTreeNode.Const((memoryUnitSize * depth).toLong())
                )
            )
        )
        val updateRbpAtDepth = IntermediateFormTreeNode.MemoryWrite(
            IntermediateFormTreeNode.Subtract(
                displayAddress,
                IntermediateFormTreeNode.Const((memoryUnitSize * depth).toLong())
            ),
            IntermediateFormTreeNode.RegisterRead(Register.RBP)
        )
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, savePreviousRbp)
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, updateRbpAtDepth)

        return cfgBuilder.build()
    }

    override fun genEpilogue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // restore previous rbp in display at depth
        val restorePreviousDisplayEntry = IntermediateFormTreeNode.MemoryWrite(
            IntermediateFormTreeNode.Subtract(
                displayAddress,
                IntermediateFormTreeNode.Const((memoryUnitSize * depth).toLong())
            ),
            IntermediateFormTreeNode.RegisterRead(previousDisplayEntryRegister)
        )
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, restorePreviousDisplayEntry)

        // restore rsp
        val movRspRbp = IntermediateFormTreeNode.RegisterWrite(
            Register.RSP,
            IntermediateFormTreeNode.RegisterRead(Register.RBP)
        )
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, movRspRbp)

        // restore callee-saved registers
        for (register in caleeSavedRegistersWithoutRSP)
            cfgBuilder.addLinkFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IntermediateFormTreeNode.RegisterWrite(register, IntermediateFormTreeNode.StackPop())
            )

        return cfgBuilder.build()
    }

    override fun genRead(variable: NamedNode, isDirect: Boolean): IntermediateFormTreeNode {
        if (isDirect) {
            return when (variablesLocationTypes[variable]!!) {
                VariableLocationType.MEMORY -> {
                    IntermediateFormTreeNode.MemoryRead(
                        IntermediateFormTreeNode.Subtract(
                            IntermediateFormTreeNode.RegisterRead(Register.RBP),
                            IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                        )
                    )
                }

                VariableLocationType.REGISTER ->
                    IntermediateFormTreeNode.RegisterRead(variablesRegisters[variable]!!)
            }
        } else {
            if (variablesLocationTypes[variable]!! == VariableLocationType.REGISTER)
                throw IndirectReadFromRegister()

            val displayElementAddress = IntermediateFormTreeNode.Subtract(
                displayAddress,
                IntermediateFormTreeNode.Const((memoryUnitSize * depth).toLong())
            )
            return IntermediateFormTreeNode.MemoryRead(
                IntermediateFormTreeNode.Subtract(
                    IntermediateFormTreeNode.MemoryRead(displayElementAddress),
                    IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                )
            )
        }
    }

    override fun genWrite(variable: NamedNode, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode {
        if (isDirect) {
            return when (variablesLocationTypes[variable]!!) {
                VariableLocationType.MEMORY -> {
                    IntermediateFormTreeNode.MemoryWrite(
                        IntermediateFormTreeNode.Subtract(
                            IntermediateFormTreeNode.RegisterRead(Register.RBP),
                            IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                        ),
                        value
                    )
                }

                VariableLocationType.REGISTER ->
                    IntermediateFormTreeNode.RegisterWrite(variablesRegisters[variable]!!, value)
            }
        } else {
            if (variablesLocationTypes[variable]!! == VariableLocationType.REGISTER)
                throw IndirectReadFromRegister()

            val displayElementAddress = IntermediateFormTreeNode.Subtract(
                displayAddress,
                IntermediateFormTreeNode.Const((memoryUnitSize * depth).toLong())
            )
            return IntermediateFormTreeNode.MemoryWrite(
                IntermediateFormTreeNode.Subtract(
                    IntermediateFormTreeNode.MemoryRead(displayElementAddress),
                    IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                ),
                value
            )
        }
    }

    class IndirectReadFromRegister : Exception()
}
