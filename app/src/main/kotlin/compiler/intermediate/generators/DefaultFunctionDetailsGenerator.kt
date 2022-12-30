package compiler.intermediate.generators

import compiler.ast.NamedNode
import compiler.ast.Variable
import compiler.intermediate.CFGLinkType
import compiler.intermediate.ConstantAlignedToAGivenRestModulo
import compiler.intermediate.ConstantPlaceholder
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import compiler.intermediate.SummedConstant
import compiler.utils.referenceHashMapOf

enum class VariableLocationType {
    MEMORY,
    REGISTER
}

const val memoryUnitSize: ULong = 8u
val calleeSavedRegistersWithoutRSPAndRBP = listOf(Register.RBX, Register.R12, Register.R13, Register.R14, Register.R15)
const val DISPLAY_LABEL_IN_MEMORY = "display"

// Function Details Generator consistent with SystemV AMD64 calling convention
data class DefaultFunctionDetailsGenerator(
    val parameters: List<NamedNode>,
    val variableToStoreFunctionResult: Variable?,
    val functionLocationInCode: IFTNode.MemoryLabel,
    val depth: ULong,
    val variablesLocationTypes: Map<NamedNode, VariableLocationType>, // should contain parameters
    val displayAddress: IFTNode,
    val createRegisterFor: (NamedNode) -> Register = { Register() } // for testing
) : FunctionDetailsGenerator {

    private val variablesStackOffsets: MutableMap<NamedNode, ULong> = referenceHashMapOf()
    private val variablesRegisters: MutableMap<NamedNode, Register> = referenceHashMapOf()
    private var variablesTotalOffset: ULong = 0u
    private val previousDisplayEntryRegister = Register()
    private val calleeSavedBackupRegisters = calleeSavedRegistersWithoutRSPAndRBP.map { Register() }

    override val spilledRegistersOffset: ConstantPlaceholder = ConstantPlaceholder()

    init {
        for ((variable, locationType) in variablesLocationTypes.entries) {
            when (locationType) {
                VariableLocationType.MEMORY -> {
                    variablesTotalOffset += memoryUnitSize
                    variablesStackOffsets[variable] = variablesTotalOffset
                }

                VariableLocationType.REGISTER -> {
                    variablesRegisters[variable] = createRegisterFor(variable)
                }
            }
        }
    }

    override fun genCall(
        args: List<IFTNode>,
    ): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        return SysV64CallingConvention.genCall(functionLocationInCode, args, variableToStoreFunctionResult !== null)
    }

    override fun genPrologue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // backup rbp
        cfgBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.StackPush(IFTNode.RegisterRead(Register.RBP))
        ) // disaligns stack

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
                    ConstantAlignedToAGivenRestModulo(
                        SummedConstant(variablesTotalOffset.toLong(), spilledRegistersOffset),
                        stackAlignmentInBytes.toLong(),
                        stackAlignmentInBytes - memoryUnitSize.toLong()
                    )
                )
            )
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, subRsp)

        // update display
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
                            IFTNode.Const((param.index + 2) * memoryUnitSize.toLong())
                        )
                    ),
                    true
                ),
            )
        }

        // backup callee-saved registers
        for ((hardwareReg, backupReg) in calleeSavedRegistersWithoutRSPAndRBP zip calleeSavedBackupRegisters)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(backupReg, IFTNode.RegisterRead(hardwareReg))
            )

        return cfgBuilder.build()
    }

    override fun genEpilogue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // restore callee-saved registers
        for ((hardwareReg, backupReg) in calleeSavedRegistersWithoutRSPAndRBP zip calleeSavedBackupRegisters)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(hardwareReg, IFTNode.RegisterRead(backupReg))
            )

        // move result to RAX
        if (variableToStoreFunctionResult != null)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(Register.RAX, genRead(variableToStoreFunctionResult, true))
            )

        // restore previous rbp in display at depth
        val restorePreviousDisplayEntry = IFTNode.MemoryWrite(
            displayElementAddress(),
            IFTNode.RegisterRead(previousDisplayEntryRegister)
        )
        cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, restorePreviousDisplayEntry)

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

    private fun genAccess(namedNode: NamedNode, isDirect: Boolean, regAccessGenerator: (Register) -> IFTNode, memAccessGenerator: (IFTNode) -> IFTNode): IFTNode {
        // requires correct value for RBP register
        return if (isDirect) {
            when (variablesLocationTypes[namedNode]!!) {
                VariableLocationType.MEMORY -> {
                    memAccessGenerator(
                        IFTNode.Subtract(
                            IFTNode.RegisterRead(Register.RBP),
                            IFTNode.Const(variablesStackOffsets[namedNode]!!.toLong())
                        )
                    )
                }

                VariableLocationType.REGISTER ->
                    regAccessGenerator(variablesRegisters[namedNode]!!)
            }
        } else {
            if (variablesLocationTypes[namedNode]!! == VariableLocationType.REGISTER)
                throw IndirectRegisterAccess()

            memAccessGenerator(
                IFTNode.Subtract(
                    IFTNode.MemoryRead(displayElementAddress()),
                    IFTNode.Const(variablesStackOffsets[namedNode]!!.toLong())
                )
            )
        }
    }

    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode =
        genAccess(namedNode, isDirect, { IFTNode.RegisterRead(it) }, { IFTNode.MemoryRead(it) })

    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode =
        genAccess(namedNode, isDirect, { IFTNode.RegisterWrite(it, value) }, { IFTNode.MemoryWrite(it, value) })

    private fun displayElementAddress(): IFTNode {
        return IFTNode.Add(
            displayAddress,
            IFTNode.Const((memoryUnitSize * depth).toLong())
        )
    }

    class IndirectRegisterAccess : Exception()
}
