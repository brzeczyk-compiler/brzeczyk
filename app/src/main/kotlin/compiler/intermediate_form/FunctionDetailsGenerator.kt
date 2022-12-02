package compiler.intermediate_form

import compiler.ast.NamedNode
import compiler.ast.Variable
import compiler.common.reference_collections.ReferenceHashMap

data class FunctionCallIntermediateForm(
    val callGraph: ControlFlowGraph,
    val result: IntermediateFormTreeNode?
)

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
    val displayAddress: MemoryAddress,
    val createRegisterFor: (NamedNode) -> Register = { Register() } // for testing
) {

    private val variablesStackOffsets: MutableMap<NamedNode, ULong> = ReferenceHashMap()
    private val variablesRegisters: MutableMap<NamedNode, Register> = ReferenceHashMap()
    private var prologueOffset: ULong = 0u

    init {
        for ((variable, locationType) in variablesLocationTypes.entries) {
            when (locationType) {
                VariableLocationType.MEMORY -> {
                    variablesStackOffsets[variable] = 3u * memoryUnitSize + prologueOffset
                    prologueOffset += memoryUnitSize
                }

                VariableLocationType.REGISTER -> {
                    // create a register for the variable TODO: is some register assignment logic already needed here?
                    variablesRegisters[variable] = createRegisterFor(variable)
                }
            }
        }
    }

    fun genCall(
        args: List<IntermediateFormTreeNode>,
    ): FunctionCallIntermediateForm {
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
        return FunctionCallIntermediateForm(cfgBuilder.build(), readResultNode)
    }

    fun genPrologue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // move args from Regsters and Stack
        for ((param, register) in parameters zip argPositionToRegister) {
            cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL,
                genWrite(
                    param,
                    IntermediateFormTreeNode.RegisterRead(register),
                    true
                )
            )
        }
        for (param in parameters.drop(argPositionToRegister.size)) {
            cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL,
                genWrite(
                    param,
                    IntermediateFormTreeNode.StackPop(),
                    true
                )
            )
        }

        // backup other calee-saved registers
        for(register in caleeSavedRegistersWithoutRSP.reversed())
            cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL,
                IntermediateFormTreeNode.StackPush(IntermediateFormTreeNode.RegisterRead(register))
            )

        val movRbpRsp = IntermediateFormTreeNode.RegisterWrite(
            Register.RBP,
            IntermediateFormTreeNode.RegisterRead(Register.RSP)
        )
        cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL, movRbpRsp)


        var last: Pair<IFTNode, CFGLinkType> = Pair(movRbpRsp, CFGLinkType.UNCONDITIONAL)

        // update display
        val savePreviousRbp = IntermediateFormTreeNode.StackPush(
            IntermediateFormTreeNode.MemoryRead(
                IntermediateFormTreeNode.Const((displayAddress + memoryUnitSize * depth).toLong())
            )
        )
        val updateRbpAtDepth = IntermediateFormTreeNode.MemoryWrite(
            IntermediateFormTreeNode.Const((displayAddress + memoryUnitSize * depth).toLong()),
            IntermediateFormTreeNode.RegisterRead(Register.RBP)
        )
        cfgBuilder.addLink(last, savePreviousRbp)
        last = Pair(savePreviousRbp, CFGLinkType.UNCONDITIONAL)
        cfgBuilder.addLink(last, updateRbpAtDepth)

        // allocate memory and registers for local variables
        cfgBuilder.addLinkFromAllFinalRoots(
            CFGLinkType.UNCONDITIONAL,
            IntermediateFormTreeNode.RegisterWrite(
                Register.RSP,
                IntermediateFormTreeNode.Add(
                    IntermediateFormTreeNode.RegisterRead(Register.RSP),
                    IntermediateFormTreeNode.Const(prologueOffset.toLong())
                )
            )
        )

        return cfgBuilder.build()
    }

    fun genEpilogue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // abandon stack variables
        val addRspOffset = IntermediateFormTreeNode.RegisterWrite(
            Register.RSP,
            IntermediateFormTreeNode.Add(
                IntermediateFormTreeNode.RegisterRead(Register.RSP),
                IntermediateFormTreeNode.Const(prologueOffset.toLong())
            )
        )

        cfgBuilder.addLink(null, addRspOffset)
        var last: Pair<IFTNode, CFGLinkType> = Pair(addRspOffset, CFGLinkType.UNCONDITIONAL)

        // restore previous rbp in display at depth
        val popPreviousRbp = IntermediateFormTreeNode.MemoryWrite(
            IntermediateFormTreeNode.Const((displayAddress + memoryUnitSize * depth).toLong()),
            IntermediateFormTreeNode.StackPop()
        )

        cfgBuilder.addLink(last, popPreviousRbp)
        last = Pair(popPreviousRbp, CFGLinkType.UNCONDITIONAL)

        // restore rbp
        val movRspRbp = IntermediateFormTreeNode.RegisterWrite(
            Register.RSP,
            IntermediateFormTreeNode.RegisterRead(Register.RBP)
        )

        cfgBuilder.addLink(last, movRspRbp)

        // restore other calee-saved registers
        for(register in caleeSavedRegistersWithoutRSP)
            cfgBuilder.addLinkFromAllFinalRoots(CFGLinkType.UNCONDITIONAL,
                IntermediateFormTreeNode.RegisterWrite(register, IntermediateFormTreeNode.StackPop())
            )

        return cfgBuilder.build()
    }

    fun genRead(variable: NamedNode, isDirect: Boolean): IntermediateFormTreeNode {
        if (isDirect) {
            return when (variablesLocationTypes[variable]!!) {
                VariableLocationType.MEMORY -> {
                    IntermediateFormTreeNode.MemoryRead(
                        IntermediateFormTreeNode.Add(
                            IntermediateFormTreeNode.RegisterRead(Register.RBP),
                            IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                        )
                    )
                }
                VariableLocationType.REGISTER ->
                    IntermediateFormTreeNode.RegisterRead(variablesRegisters[variable]!!)
            }
        } else {
            val displayElementAddress = displayAddress + memoryUnitSize * depth
            return IntermediateFormTreeNode.MemoryRead(
                IntermediateFormTreeNode.Add(
                    IntermediateFormTreeNode.MemoryRead(IntermediateFormTreeNode.Const(displayElementAddress.toLong())),
                    IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                )
            )
        }
    }

    fun genWrite(variable: NamedNode, value: IntermediateFormTreeNode, isDirect: Boolean): IntermediateFormTreeNode {
        if (isDirect) {
            return when (variablesLocationTypes[variable]!!) {
                VariableLocationType.MEMORY -> {
                    IntermediateFormTreeNode.MemoryWrite(
                        IntermediateFormTreeNode.Add(
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
            val displayElementAddress = displayAddress + memoryUnitSize * depth
            return IntermediateFormTreeNode.MemoryWrite(
                IntermediateFormTreeNode.Add(
                    IntermediateFormTreeNode.MemoryRead(IntermediateFormTreeNode.Const(displayElementAddress.toLong())),
                    IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                ),
                value
            )
        }
    }
}
