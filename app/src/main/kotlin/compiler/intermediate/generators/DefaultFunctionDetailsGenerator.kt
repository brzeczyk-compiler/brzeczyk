package compiler.intermediate.generators

import compiler.ast.NamedNode
import compiler.ast.Variable
import compiler.intermediate.AlignedConstant
import compiler.intermediate.CFGLinkType
import compiler.intermediate.ConstantPlaceholder
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import compiler.intermediate.SummedConstant
import compiler.utils.Ref
import compiler.utils.mutableKeyRefMapOf

enum class VariableLocationType {
    MEMORY,
    REGISTER
}

const val MEMORY_UNIT_SIZE: ULong = 8u

val calleeSavedRegistersWithoutRSPAndRBP = listOf(Register.RBX, Register.R12, Register.R13, Register.R14, Register.R15)

const val DISPLAY_LABEL_IN_MEMORY = "display"

// Function Details Generator consistent with SystemV AMD64 calling convention
data class DefaultFunctionDetailsGenerator(
    val parameters: List<NamedNode>,
    val variableToStoreFunctionResult: Variable?,
    val functionLocationInCode: IFTNode.MemoryLabel,
    val depth: ULong,
    val variablesLocationTypes: Map<Ref<NamedNode>, VariableLocationType>, // should contain parameters
    val displayAddress: IFTNode,
    val createRegisterFor: (NamedNode) -> Register = { Register() } // for testing
) : FunctionDetailsGenerator {

    private val variablesStackOffsets: MutableMap<Ref<NamedNode>, ULong> = mutableKeyRefMapOf()
    private val variablesRegisters: MutableMap<Ref<NamedNode>, Register> = mutableKeyRefMapOf()
    private var variablesRegionSize: ULong = 0u
    private val previousDisplayEntryRegister = Register()
    private val calleeSavedBackupRegisters = calleeSavedRegistersWithoutRSPAndRBP.map { Register() }

    override val spilledRegistersRegionOffset: ULong get() = variablesRegionSize
    override val spilledRegistersRegionSize: ConstantPlaceholder = ConstantPlaceholder()
    val requiredMemoryBelowRBP get() = SummedConstant(variablesRegionSize.toLong(), spilledRegistersRegionSize)
    override val identifier: String = functionLocationInCode.label
    init {
        for ((variable, locationType) in variablesLocationTypes.entries) {
            when (locationType) {
                VariableLocationType.MEMORY -> {
                    variablesRegionSize += MEMORY_UNIT_SIZE
                    variablesStackOffsets[variable] = variablesRegionSize
                }

                VariableLocationType.REGISTER -> {
                    variablesRegisters[variable] = createRegisterFor(variable.value)
                }
            }
        }
    }

    override fun genCall(
        args: List<IFTNode>,
    ): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        return SysV64CallingConvention.genCall(functionLocationInCode, args, if (variableToStoreFunctionResult !== null) 1 else 0)
    }

    fun genDisplayUpdate(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        val savePreviousRbp = IFTNode.RegisterWrite(
            previousDisplayEntryRegister,
            IFTNode.MemoryRead(displayElementAddress())
        )
        val updateRbpAtDepth = IFTNode.MemoryWrite(
            displayElementAddress(),
            IFTNode.RegisterRead(Register.RBP)
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, savePreviousRbp)
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, updateRbpAtDepth)

        return cfgBuilder.build()
    }

    fun genCalleeSavedRegistersBackup(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        for ((hardwareReg, backupReg) in calleeSavedRegistersWithoutRSPAndRBP zip calleeSavedBackupRegisters)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(backupReg, IFTNode.RegisterRead(hardwareReg))
            )

        return cfgBuilder.build()
    }

    override fun genPrologue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // backup rbp
        cfgBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.StackPush(IFTNode.RegisterRead(Register.RBP))
        )

        // update rbp
        val movRbpRsp = IFTNode.RegisterWrite(
            Register.RBP,
            IFTNode.RegisterRead(Register.RSP)
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, movRbpRsp)

        // allocate memory for local variables and spills
        val subRsp = IFTNode.RegisterWrite(
            Register.RSP,
            IFTNode.Subtract(
                IFTNode.RegisterRead(Register.RSP),
                // make stack aligned back
                IFTNode.Const(
                    AlignedConstant(
                        requiredMemoryBelowRBP,
                        STACK_ALIGNMENT_IN_BYTES.toLong(),
                        STACK_ALIGNMENT_IN_BYTES - 2 * MEMORY_UNIT_SIZE.toLong() // -2 to account return address and backed up rbp
                    )
                )
            )
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, subRsp)

        // update display
        cfgBuilder.mergeUnconditionally(genDisplayUpdate())

        // move args from registers and stack
        for ((param, register) in parameters zip argPositionToRegister) {
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                genWrite(
                    param,
                    IFTNode.RegisterRead(register),
                    true
                )
            )
        }
        for (param in parameters.drop(argPositionToRegister.size).withIndex()) {
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                genWrite(
                    param.value,
                    IFTNode.MemoryRead(
                        IFTNode.Add(
                            IFTNode.RegisterRead(Register.RBP), // add 2 to account old RBP and return address
                            IFTNode.Const((param.index + 2) * MEMORY_UNIT_SIZE.toLong())
                        )
                    ),
                    true
                ),
            )
        }

        // backup callee-saved registers
        cfgBuilder.mergeUnconditionally(genCalleeSavedRegistersBackup())

        return cfgBuilder.build()
    }

    fun genCalleeSavedRegistersRestore(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        for ((hardwareReg, backupReg) in calleeSavedRegistersWithoutRSPAndRBP zip calleeSavedBackupRegisters)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(hardwareReg, IFTNode.RegisterRead(backupReg))
            )

        return cfgBuilder.build()
    }

    fun genDisplayRestore(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        val restorePreviousDisplayEntry = IFTNode.MemoryWrite(
            displayElementAddress(),
            IFTNode.RegisterRead(previousDisplayEntryRegister)
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, restorePreviousDisplayEntry)

        return cfgBuilder.build()
    }

    override fun genEpilogue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // restore callee-saved registers
        cfgBuilder.mergeUnconditionally(genDisplayRestore())

        // move result to RAX
        if (variableToStoreFunctionResult != null)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(Register.RAX, genRead(variableToStoreFunctionResult, true))
            )

        // restore previous rbp in display at depth
        cfgBuilder.mergeUnconditionally(genCalleeSavedRegistersRestore())

        // restore rsp
        val movRspRbp = IFTNode.RegisterWrite(
            Register.RSP,
            IFTNode.RegisterRead(Register.RBP)
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, movRspRbp)

        // restore rbp
        cfgBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.RegisterWrite(Register.RBP, IFTNode.StackPop())
        )

        // return
        cfgBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.Return(setOf(Register.RAX) + calleeSavedRegistersWithoutRSPAndRBP)
        )

        return cfgBuilder.build()
    }

    private fun genAccess(
        namedNode: NamedNode,
        isDirect: Boolean,
        regAccessGenerator: (Register) -> IFTNode,
        memAccessGenerator: (IFTNode) -> IFTNode,
        baseRegister: Register
    ): IFTNode {
        // requires correct value for RBP register
        return if (isDirect) {
            when (variablesLocationTypes[Ref(namedNode)]!!) {
                VariableLocationType.MEMORY -> {
                    memAccessGenerator(
                        IFTNode.Subtract(
                            IFTNode.RegisterRead(baseRegister),
                            IFTNode.Const(variablesStackOffsets[Ref(namedNode)]!!.toLong())
                        )
                    )
                }

                VariableLocationType.REGISTER ->
                    regAccessGenerator(variablesRegisters[Ref(namedNode)]!!)
            }
        } else {
            if (variablesLocationTypes[Ref(namedNode)]!! == VariableLocationType.REGISTER)
                throw IndirectRegisterAccess()

            memAccessGenerator(
                IFTNode.Subtract(
                    IFTNode.MemoryRead(displayElementAddress()),
                    IFTNode.Const(variablesStackOffsets[Ref(namedNode)]!!.toLong())
                )
            )
        }
    }

    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode =
        genAccess(namedNode, isDirect, { IFTNode.RegisterRead(it) }, { IFTNode.MemoryRead(it) }, Register.RBP)

    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode =
        genAccess(namedNode, isDirect, { IFTNode.RegisterWrite(it, value) }, { IFTNode.MemoryWrite(it, value) }, Register.RBP)

    fun genDirectReadWithCustomBase(namedNode: NamedNode, baseRegister: Register) =
        genAccess(namedNode, true, { IFTNode.RegisterRead(it) }, { IFTNode.MemoryRead(it) }, baseRegister)

    fun genDirectWriteWithCustomBase(namedNode: NamedNode, value: IFTNode, baseRegister: Register) =
        genAccess(namedNode, true, { IFTNode.RegisterWrite(it, value) }, { IFTNode.MemoryWrite(it, value) }, baseRegister)

    private fun displayElementAddress(): IFTNode {
        return IFTNode.Add(
            displayAddress,
            IFTNode.Const((MEMORY_UNIT_SIZE * depth).toLong())
        )
    }

    class IndirectRegisterAccess : Exception()
}
