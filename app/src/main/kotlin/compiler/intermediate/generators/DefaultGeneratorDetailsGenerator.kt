package compiler.intermediate.generators

import compiler.ast.Function
import compiler.ast.NamedNode
import compiler.ast.Type
import compiler.ast.Variable
import compiler.intermediate.CFGLinkType
import compiler.intermediate.ControlFlowGraph
import compiler.intermediate.ControlFlowGraphBuilder
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import compiler.utils.Ref

class DefaultGeneratorDetailsGenerator(
    private val parameters: List<NamedNode>,
    initLabel: IFTNode.MemoryLabel,
    private val resumeLabel: IFTNode.MemoryLabel,
    finalizeLabel: IFTNode.MemoryLabel,
    depth: ULong,
    variablesLocationTypes: Map<Ref<NamedNode>, VariableLocationType>,
    displayAddress: IFTNode
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

    override fun genInit(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // generate the prologue
        cfgBuilder.mergeUnconditionally(initFDG.genPrologue())

        // Allocate "stack frame" on heap
        val mallocCall = mallocFDG.genCall(listOf(IFTNode.Const(innerFDG.requiredMemoryBelowRBP)))
        cfgBuilder.mergeUnconditionally(mallocCall.callGraph)
        cfgBuilder.addSingleTree(initFDG.genWrite(initResultVariable, mallocCall.result!!, true))
        // TODO: check whether allocation was successful

        // prepare custom base
        val customBaseRegister = Register.RCX // FIXME: consider using a virtual register here
        cfgBuilder.addSingleTree(
            IFTNode.RegisterWrite(
                customBaseRegister,
                IFTNode.Add(
                    initFDG.genRead(initResultVariable, true),
                    IFTNode.Const(innerFDG.requiredMemoryBelowRBP)
                )
            )
        )

        // copy arguments
        for (parameter in parameters)
            cfgBuilder.addSingleTree(
                innerFDG.genDirectWriteWithCustomBase(
                    parameter,
                    initFDG.genRead(parameter, true),
                    customBaseRegister
                )
            )

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
        cfgBuilder.addSingleTree(
            IFTNode.RegisterWrite(
                Register.RBP,
                IFTNode.Add(
                    IFTNode.RegisterRead(framePointerRegister),
                    IFTNode.Const(innerFDG.requiredMemoryBelowRBP)
                )
            )
        )

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

    override fun genFinalize(): ControlFlowGraph {
        val cfgBuilder = ControlFlowGraphBuilder()

        // generate the prologue
        cfgBuilder.mergeUnconditionally(finalizeFDG.genPrologue())

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
        val mallocFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("malloc"), 1)
        val freeFDG = ForeignFunctionDetailsGenerator(IFTNode.MemoryLabel("free"), 0)
    }

    private val innerFDG = DefaultFunctionDetailsGenerator(
        parameters,
        null,
        IFTNode.MemoryLabel(""), // doesn't matter, since we never call it
        depth,
        variablesLocationTypes +
            parameters.associate { Ref(it) to VariableLocationType.MEMORY },
        displayAddress
    )

    private val initResultVariable = Variable(Variable.Kind.VARIABLE, "dummy", Type.Number, null, null)
    private val initFDG = DefaultFunctionDetailsGenerator(
        parameters,
        initResultVariable,
        initLabel,
        depth,
        (parameters + listOf(initResultVariable)).associate { Ref(it) to VariableLocationType.REGISTER },
        displayAddress
    )

    private val finalizeFramePointerParameter = Function.Parameter("framePointer", Type.Number, null, null)
    private val finalizeFDG = DefaultFunctionDetailsGenerator(
        listOf(finalizeFramePointerParameter),
        null,
        finalizeLabel,
        depth,
        mapOf(Ref(finalizeFramePointerParameter) to VariableLocationType.REGISTER),
        displayAddress
    )
}
