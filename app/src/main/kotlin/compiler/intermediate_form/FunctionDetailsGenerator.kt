package compiler.intermediate_form

import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Type
import compiler.common.reference_collections.ReferenceHashMap

data class FunctionCallIntermediateForm(
    val callGraph: ControlFlowGraph,
    val result: IntermediateFormTreeNode?
)

enum class VariableLocationType {
    MEMORY,
    REGISTER
}

val FUNCTION_RESULT_REGISTER = Register()
val BASE_POINTER_REGISTER = Register()
val STACK_POINTER_REGISTER = Register()

data class FunctionDetailsGenerator(
    val parameters: List<NamedNode>, // argument Variable na wynik wywołania
    val functionCFG: ControlFlowGraph,
    val function: Function,
    val depth: ULong,
    val variablesLocationTypes: Map<NamedNode, VariableLocationType>, // should contain parameters ???
    val displayAddress: MemoryAddress // IFTNode
) {

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
                    variablesRegisters[variable] = Register()
                }
            }
        }
    }

    fun genCall(
        args: List<IntermediateFormTreeNode>,
    ): FunctionCallIntermediateForm {
        // First, it moves parameter values to appropriate location based on args (using genWrite).
        // At the end it adds an instruction to store function result in FUNCTION_RESULT_REGISTER, when it isn't Unit
        // returning function, and then it creates temporary IFTNode outside CFG in result,
        // which simply reads value from that register.

        val cfgBuilder = ControlFlowGraphBuilder()

        var last: Pair<IFTNode, CFGLinkType>? = null
        // write arg values to params
        for ((arg, param) in args zip parameters) {
            val node = genWrite(param, arg, false)
            cfgBuilder.addLink(last, node)
            last = Pair(node, CFGLinkType.UNCONDITIONAL)
        }

        // add function graph
        cfgBuilder.addAllFrom(functionCFG, false)
        cfgBuilder.addLink(last, functionCFG.entryTreeRoot!!)

        fun modifyReturnNodesToStoreResultInAppropriateRegister(functionCfg: ControlFlowGraph): FunctionCallIntermediateForm {
            val modifiedCfgBuilder = ControlFlowGraphBuilder()
            modifiedCfgBuilder.addAllFrom(functionCfg, true)
            modifiedCfgBuilder.updateNodes({ it in functionCfg.finalTreeRoots }, {
                IntermediateFormTreeNode.RegisterWrite(FUNCTION_RESULT_REGISTER, it)
            })

            return FunctionCallIntermediateForm(
                modifiedCfgBuilder.build(),
                IntermediateFormTreeNode.RegisterRead(FUNCTION_RESULT_REGISTER)
            )
        }

        if (function.returnType == Type.Unit)
            return FunctionCallIntermediateForm(cfgBuilder.build(), null)
        return modifyReturnNodesToStoreResultInAppropriateRegister(cfgBuilder.build())
    }

    fun genPrologue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // save rbp
        val pushRbp = IntermediateFormTreeNode.StackPush(IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER))
        val movRbpRsp = IntermediateFormTreeNode.RegisterWrite(
            BASE_POINTER_REGISTER,
            IntermediateFormTreeNode.RegisterRead(STACK_POINTER_REGISTER)
        )

        cfgBuilder.addLink(null, pushRbp)
        cfgBuilder.addLink(Pair(pushRbp, CFGLinkType.UNCONDITIONAL), movRbpRsp)
        var last: Pair<IFTNode, CFGLinkType> = Pair(movRbpRsp, CFGLinkType.UNCONDITIONAL)

        // allocate memory for local variables
        val subRsp = IntermediateFormTreeNode.RegisterWrite(
            STACK_POINTER_REGISTER,
            IntermediateFormTreeNode.Subtract(
                IntermediateFormTreeNode.RegisterRead(STACK_POINTER_REGISTER),
                IntermediateFormTreeNode.Const(variablesTotalOffset.toLong())
            )
        )

        cfgBuilder.addLink(last, subRsp)
        last = Pair(subRsp, CFGLinkType.UNCONDITIONAL)

        // update display
        val savePreviousRbp = IntermediateFormTreeNode.RegisterWrite(
            previousDisplayEntryRegister,
            IntermediateFormTreeNode.MemoryRead(
                IntermediateFormTreeNode.Const((displayAddress - memoryUnitSize * depth).toLong())
            )
        )
        val updateRbpAtDepth = IntermediateFormTreeNode.MemoryWrite(
            IntermediateFormTreeNode.Const((displayAddress - memoryUnitSize * depth).toLong()),
            IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER)
        )

        cfgBuilder.addLink(last, savePreviousRbp)
        last = Pair(savePreviousRbp, CFGLinkType.UNCONDITIONAL)
        cfgBuilder.addLink(last, updateRbpAtDepth)
        last = Pair(updateRbpAtDepth, CFGLinkType.UNCONDITIONAL)

        // obsłuż konwencję + zapis callee safe registers

        return cfgBuilder.build()
    }

    fun genEpilogue(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // abandon stack variables
        val addRspOffset = IntermediateFormTreeNode.RegisterWrite(
            STACK_POINTER_REGISTER,
            IntermediateFormTreeNode.Add(
                IntermediateFormTreeNode.RegisterRead(STACK_POINTER_REGISTER),
                IntermediateFormTreeNode.Const(variablesTotalOffset.toLong())
            )
        )

        cfgBuilder.addLink(null, addRspOffset)
        var last: Pair<IFTNode, CFGLinkType> = Pair(addRspOffset, CFGLinkType.UNCONDITIONAL)

        // restore previous rbp in display at depth ???
        val popPreviousRbp = IntermediateFormTreeNode.StackPopToMemory(
            IntermediateFormTreeNode.Const((displayAddress - memoryUnitSize * depth).toLong()),
        )

        cfgBuilder.addLink(last, popPreviousRbp)
        last = Pair(popPreviousRbp, CFGLinkType.UNCONDITIONAL)

        // restore rbp
        val movRspRbp = IntermediateFormTreeNode.RegisterWrite(
            STACK_POINTER_REGISTER,
            IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER)
        )
        val popRbp = IntermediateFormTreeNode.StackPopToRegister(BASE_POINTER_REGISTER)

        cfgBuilder.addLink(last, movRspRbp)
        last = Pair(movRspRbp, CFGLinkType.UNCONDITIONAL)
        cfgBuilder.addLink(last, popRbp)

        // przywróć callee safe ale przed przywracaniem rsp

        return cfgBuilder.build()
    }

    fun genRead(variable: NamedNode, isDirect: Boolean): IntermediateFormTreeNode {
        if (isDirect) {
            return when (variablesLocationTypes[variable]!!) {
                VariableLocationType.MEMORY -> {
                    IntermediateFormTreeNode.MemoryRead(
                        IntermediateFormTreeNode.Add( // Sub
                            IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER),
                            IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                        )
                    )
                }
                VariableLocationType.REGISTER ->
                    IntermediateFormTreeNode.RegisterRead(variablesRegisters[variable]!!)
            }
        } else {
            val displayElementAddress = displayAddress - memoryUnitSize * depth // IFTNode
            return IntermediateFormTreeNode.MemoryRead(
                IntermediateFormTreeNode.Add( // maybe Sub
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
                            IntermediateFormTreeNode.RegisterRead(BASE_POINTER_REGISTER),
                            IntermediateFormTreeNode.Const(variablesStackOffsets[variable]!!.toLong())
                        ),
                        value
                    )
                }
                VariableLocationType.REGISTER ->
                    IntermediateFormTreeNode.RegisterWrite(variablesRegisters[variable]!!, value)
            }
        } else {
            val displayElementAddress = displayAddress - memoryUnitSize * depth
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
