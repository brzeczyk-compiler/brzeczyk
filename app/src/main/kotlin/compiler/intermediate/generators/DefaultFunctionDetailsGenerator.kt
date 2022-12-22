package compiler.intermediate.generators

import compiler.ast.NamedNode
import compiler.ast.Variable
import compiler.intermediate.CFGLinkType
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
val argPositionToRegister = listOf(Register.RDI, Register.RSI, Register.RDX, Register.RCX, Register.R8, Register.R9)
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
        val cfgBuilder = ControlFlowGraphBuilder()

        // First, move arguments to appropriate registers (or push to stack) according to call convention.
        for ((arg, register) in args zip argPositionToRegister) {
            val node = IFTNode.RegisterWrite(register, arg)
            cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, node)
        }

        var numberOfArgsPushedToStack = 0
        for (arg in args.drop(argPositionToRegister.size).reversed()) {
            val node = IFTNode.StackPush(arg)
            cfgBuilder.addLinksFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, node)
            numberOfArgsPushedToStack += 1
        }

        // Add call instruction to actually call a given function
        cfgBuilder.addLinksFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IFTNode.Call(functionLocationInCode)
        )

        // Abandon arguments that were previously put on stack
        if (numberOfArgsPushedToStack > 0)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.Add(
                    IFTNode.RegisterRead(Register.RSP),
                    IFTNode.Const(numberOfArgsPushedToStack * memoryUnitSize.toLong())
                )
            )

        // At the end create IFTNode to get function result
        val readResultNode: IFTNode? =
            if (variableToStoreFunctionResult != null) IFTNode.RegisterRead(Register.RAX)
            else null
        return FunctionDetailsGenerator.FunctionCallIntermediateForm(cfgBuilder.build(), readResultNode)
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

        // allocate memory for local variables
        val subRsp = IFTNode.RegisterWrite(
            Register.RSP,
            IFTNode.Subtract(
                IFTNode.RegisterRead(Register.RSP),
                IFTNode.Const(SummedConstant(variablesTotalOffset.toLong(), spilledRegistersOffset))
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

        // move args from Registers and Stack
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
                    IFTNode.Add(
                        IFTNode.RegisterRead(Register.RBP), // add 2 to account old RBP and return address
                        IFTNode.Const((param.index + 2) * memoryUnitSize.toLong())
                    ),
                    true
                ),
            )
        }

        // backup callee-saved registers
        for (register in calleeSavedRegistersWithoutRSPAndRBP.reversed())
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.StackPush(IFTNode.RegisterRead(register))
            )

        return cfgBuilder.build()
    }

    override fun genEpilogue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // restore callee-saved registers
        for (register in calleeSavedRegistersWithoutRSPAndRBP)
            cfgBuilder.addLinksFromAllFinalRoots(
                CFGLinkType.UNCONDITIONAL,
                IFTNode.RegisterWrite(register, IFTNode.StackPop())
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
