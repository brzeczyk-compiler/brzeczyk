package compiler.intermediate.generators

import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Statement
import compiler.ast.Type
import compiler.ast.Variable
import compiler.intermediate.CFGLinkType
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import compiler.intermediate.SummedConstant
import compiler.utils.Ref

class DefaultGeneratorDetailsGenerator(
    private val parameters: List<NamedNode>,
    initLabel: IFTNode.MemoryLabel,
    private val resumeLabel: IFTNode.MemoryLabel,
    finalizeLabel: IFTNode.MemoryLabel,
    depth: ULong,
    variablesLocationTypes: Map<Ref<NamedNode>, VariableLocationType>,
    displayAddress: IFTNode,
    private val nestedForeachLoops: List<Ref<Statement.ForeachLoop>>,
    private val getGDGForNestedLoop: (Statement.ForeachLoop) -> GeneratorDetailsGenerator
) : GeneratorDetailsGenerator {

    override fun genInitCall(args: List<IFTNode>): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        return initFDG.genCall(args)
    }

    override fun genResumeCall(framePointer: IFTNode, savedState: IFTNode): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        return SysV64CallingConvention.genCall(resumeLabel, listOf(framePointer, savedState), 2)
    }

    override fun genFinalizeCall(framePointer: IFTNode): FunctionDetailsGenerator.FunctionCallIntermediateForm {
        return finalizeFDG.genCall(listOf(framePointer))
    }

    private fun setupBasePointer(framePointer: IFTNode, newBaseRegister: Register) =
        IFTNode.RegisterWrite(
            newBaseRegister,
            IFTNode.Add(
                framePointer,
                IFTNode.Const(frameSize)
            )
        )

    override fun genInit(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // generate the prologue
        cfgBuilder.mergeUnconditionally(initFDG.genPrologue())

        // Allocate "stack frame" on heap
        val mallocCall = mallocFDG.genCall(listOf(IFTNode.Const(frameSize)))
        cfgBuilder.mergeUnconditionally(mallocCall.callGraph)
        cfgBuilder.addSingleTree(initFDG.genWrite(initResultVariable, mallocCall.result!!, true))

        // prepare custom base
        val customBaseRegister = Register.RCX // FIXME: consider using a virtual register here
        cfgBuilder.addSingleTree(setupBasePointer(initFDG.genRead(initResultVariable, true), customBaseRegister))

        // copy arguments
        parameters.forEach {
            cfgBuilder.addSingleTree(
                innerFDG.genDirectWriteWithCustomBase(
                    it,
                    initFDG.genRead(it, true),
                    customBaseRegister
                )
            )
        }

        // clear nested loops registers
        nestedForeachLoops.forEach {
            cfgBuilder.addSingleTree(IFTNode.MemoryWrite(getNestedForeachFramePointerAddressWithCustomBase(it.value, customBaseRegister)!!, IFTNode.Const(0)))
        }

        // clear array variables
        arrayVariables.map { it.value as Variable }.forEach {
            cfgBuilder.addSingleTree(innerFDG.genDirectWriteWithCustomBase(it, IFTNode.Const(0), customBaseRegister))
        }

        // generate the epilogue
        cfgBuilder.mergeUnconditionally(initFDG.genEpilogue())

        return cfgBuilder.build()
    }

    override fun genResume(mainBody: ControlFlowGraph): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()
        val framePointerRegister = argPositionToRegister[0]
        val savedStateRegister = argPositionToRegister[1]

        // prologue

        // setup base pointer
        cfgBuilder.addSingleTree(IFTNode.StackPush(IFTNode.RegisterRead(Register.RBP)))
        cfgBuilder.addSingleTree(setupBasePointer(IFTNode.RegisterRead(framePointerRegister), Register.RBP))

        // update display entry
        cfgBuilder.mergeUnconditionally(innerFDG.genDisplayUpdate())

        // save callee-saved registers
        cfgBuilder.mergeUnconditionally(innerFDG.genCalleeSavedRegistersBackup())

        // decide where to jump
        cfgBuilder.addSingleTree(IFTNode.Equals(IFTNode.RegisterRead(savedStateRegister), IFTNode.Const(0)))
        cfgBuilder.mergeConditionally(
            mainBody,
            ControlFlowGraphBuilder().apply { addSingleTree(IFTNode.JumpToRegister(savedStateRegister)) }.build()
        )

        // epilogue

        // set RDX to signalize generator end
        cfgBuilder.addSingleTree(IFTNode.RegisterWrite(Register.RDX, IFTNode.Const(0)))

        // restore callee-saved registers
        val restoreCalleeSavedRegistersCFG = innerFDG.genCalleeSavedRegistersRestore()
        cfgBuilder.mergeUnconditionally(restoreCalleeSavedRegistersCFG)

        // restore display entry
        cfgBuilder.mergeUnconditionally(innerFDG.genDisplayRestore())

        // restore base pointer
        cfgBuilder.addSingleTree(IFTNode.RegisterWrite(Register.RBP, IFTNode.StackPop()))

        // return
        cfgBuilder.addSingleTree(
            IFTNode.Return(
                setOf(Register.RAX, Register.RDX) + calleeSavedRegistersWithoutRSPAndRBP
            )
        )

        // yield target
        val yieldTarget = IFTNode.LabeledNode(".yield", IFTNode.RegisterWrite(Register.RDX, IFTNode.StackPop()))
        cfgBuilder.addAllFrom(ControlFlowGraphBuilder().apply { addSingleTree(yieldTarget) }.build())
        cfgBuilder.addLink(Pair(yieldTarget, CFGLinkType.UNCONDITIONAL), restoreCalleeSavedRegistersCFG.entryTreeRoot!!, false)

        return cfgBuilder.build()
    }

    override fun genYield(value: IFTNode): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        cfgBuilder.addSingleTree(IFTNode.RegisterWrite(Register.RAX, value))
        cfgBuilder.addSingleTree(
            IFTNode.Call(
                IFTNode.MemoryLabel(".yield"),
                listOf(Register.RAX),
                callerSavedRegisters + calleeSavedRegistersWithoutRSPAndRBP
            )
        )

        return cfgBuilder.build()
    }

    private fun getNestedForeachFramePointerAddressWithCustomBase(foreachLoop: Statement.ForeachLoop, base: Register): IFTNode? {
        val index = nestedForeachLoops.indexOf(Ref(foreachLoop))
        return if (index != -1)
            IFTNode.Subtract(
                IFTNode.RegisterRead(base),
                IFTNode.Const(SummedConstant((index + 1) * memoryUnitSize.toLong(), innerFDG.requiredMemoryBelowRBP))
            )
        else null
    }

    override fun getNestedForeachFramePointerAddress(foreachLoop: Statement.ForeachLoop): IFTNode? =
        getNestedForeachFramePointerAddressWithCustomBase(foreachLoop, Register.RBP)

    override fun genFinalize(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // generate the prologue
        cfgBuilder.mergeUnconditionally(finalizeFDG.genPrologue())

        // prepare custom base
        val customBaseRegister = Register.R15 // R15 is callee-saved FIXME: consider using a virtual register here
        cfgBuilder.addSingleTree(setupBasePointer(finalizeFDG.genRead(finalizeFramePointerParameter, true), customBaseRegister))

        // recursively finalize all active nested foreach loops
        nestedForeachLoops.forEach {
            val getFramePointerReadNode = { IFTNode.MemoryRead(getNestedForeachFramePointerAddressWithCustomBase(it.value, customBaseRegister)!!) }

            val checkNode = IFTNode.Equals(getFramePointerReadNode(), IFTNode.Const(0))
            cfgBuilder.addSingleTree(checkNode)

            val callCFG = getGDGForNestedLoop(it.value).genFinalizeCall(getFramePointerReadNode()).callGraph
            cfgBuilder.addAllFrom(callCFG)
            cfgBuilder.addLink(Pair(checkNode, CFGLinkType.CONDITIONAL_FALSE), callCFG.entryTreeRoot!!)
        }

        // decrease reference counters of all array variables (function parameters are not included)
        arrayVariables.map { it.value as Variable }.forEach {
            cfgBuilder.mergeUnconditionally(
                ArrayMemoryManagement.genRefCountDecrement(
                    innerFDG.genDirectReadWithCustomBase(it, customBaseRegister),
                    it.type
                )
            )
        }

        // free the frame
        cfgBuilder.mergeUnconditionally(
            freeFDG.genCall(listOf(finalizeFDG.genRead(finalizeFramePointerParameter, true))).callGraph
        )

        // generate the epilogue
        cfgBuilder.mergeUnconditionally(finalizeFDG.genEpilogue())

        return cfgBuilder.build()
    }

    override fun genRead(namedNode: NamedNode, isDirect: Boolean): IFTNode =
        innerFDG.genRead(namedNode, isDirect)

    override fun genWrite(namedNode: NamedNode, value: IFTNode, isDirect: Boolean): IFTNode =
        innerFDG.genWrite(namedNode, value, isDirect)

    // Function details generators
    private companion object {
        val mallocFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("_\$checked_malloc"), 1)
        val freeFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("free"), 0)
    }

    private val arrayVariables = variablesLocationTypes.keys // this is inefficient but probably temporary solution
        .filter { it.value is Variable && it.value.type is Type.Array }

    private val innerFDG = DefaultFunctionDetailsGenerator(
        parameters,
        null,
        IFTNode.MemoryLabel(""), // doesn't matter, since we never call it
        depth,
        variablesLocationTypes +
            parameters.associate { Ref(it) to VariableLocationType.MEMORY } +
            arrayVariables.associateWith { VariableLocationType.MEMORY },
        displayAddress
    )

    private val initResultVariable = Variable(Variable.Kind.VARIABLE, "dummy", Type.Number, null, null)
    override val initFDG = DefaultFunctionDetailsGenerator(
        parameters,
        initResultVariable,
        initLabel,
        depth,
        (parameters + listOf(initResultVariable)).associate { Ref(it) to VariableLocationType.REGISTER },
        displayAddress
    )

    override val resumeFDG get() = innerFDG

    private val finalizeFramePointerParameter = Function.Parameter("framePointer", Type.Number, null, null)
    override val finalizeFDG = DefaultFunctionDetailsGenerator(
        listOf(finalizeFramePointerParameter),
        null,
        finalizeLabel,
        depth,
        mapOf(Ref(finalizeFramePointerParameter) to VariableLocationType.REGISTER),
        displayAddress
    )

    // other values
    private val frameSize = SummedConstant(nestedForeachLoops.size * memoryUnitSize.toLong(), innerFDG.requiredMemoryBelowRBP)
}
