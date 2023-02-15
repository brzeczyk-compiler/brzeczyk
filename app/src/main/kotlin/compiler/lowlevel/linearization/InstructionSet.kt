package compiler.lowlevel.linearization

import compiler.intermediate.Constant
import compiler.intermediate.IFTNode
import compiler.intermediate.Register
import compiler.lowlevel.Addressing
import compiler.lowlevel.Instruction

object InstructionSet {
    enum class Context {
        VALUE, UNCONDITIONAL, CONDITIONAL
    }

    // Pattern implementation that uses IFTPattern to match simple trees
    // The constructor takes the IFTPattern instance, set of contexts in which we want to match it (value, conditional, unconditional)
    // and a function that given a list of input registers, an output register, the context in which the pattern was matched
    // and any arguments captured by the IFTPattern (values of constant, labels, registers) returns the list of instructions.
    // By default, the matched contexts are value and unconditional
    data class InstructionPattern(
        val pattern: IFTPattern,
        val contexts: Set<Context> = setOf(Context.VALUE, Context.UNCONDITIONAL),
        val createInstructions: (List<Register>, Register, Context, Map<String, Any>) -> List<Instruction>
    ) : Pattern {
        private fun match(node: IFTNode, context: Context, targetLabel: String = "", invert: Boolean = false): Pattern.Result? {
            val treeMatch = pattern.match(node) ?: return null
            val cost = 1 // TODO better cost calculation
            val contextMap = if (context == Context.CONDITIONAL)
                mapOf("invert" to invert, "target" to targetLabel) else emptyMap()
            return Pattern.Result(treeMatch.matchedSubtrees, cost) { inRegisters, outRegister ->
                createInstructions(inRegisters, outRegister, context, treeMatch.matchedValues + contextMap)
            }
        }

        override fun matchValue(node: IFTNode): Pattern.Result? {
            return if (contexts.contains(Context.VALUE)) match(node, Context.VALUE) else null
        }

        override fun matchUnconditional(node: IFTNode): Pattern.Result? {
            return if (contexts.contains(Context.UNCONDITIONAL)) match(node, Context.UNCONDITIONAL) else null
        }

        override fun matchConditional(
            node: IFTNode,
            targetLabel: String,
            invert: Boolean
        ): Pattern.Result? {
            return if (contexts.contains(Context.CONDITIONAL)) match(node, Context.CONDITIONAL, targetLabel, invert) else null
        }
    }

    fun getInstructionSet(): List<Pattern> {
        return listOf(
            InstructionPattern(IFTPattern.MemoryRead()) {
                inRegisters, outRegister, _, _ ->
                listOf(Instruction.InPlaceInstruction.MoveRM(outRegister, Addressing.Base(inRegisters[0]))) // MOV  out,  [reg0]
            },
            InstructionPattern(IFTPattern.MemoryLabel(IFTPattern.AnyArgument("label"))) {
                _, outRegister, _, args ->
                listOf(
                    Instruction.InPlaceInstruction.Lea( // LEA  out,  [label]
                        outRegister,
                        Addressing.Displacement(Addressing.MemoryAddress.Label(args["label"] as String))
                    )
                )
            },
            InstructionPattern(IFTPattern.RegisterRead(IFTPattern.AnyArgument("reg"))) {
                _, outRegister, _, args ->
                listOf(Instruction.InPlaceInstruction.MoveRR(outRegister, args["reg"] as Register)) // MOV  out,  reg
            },
            InstructionPattern(IFTPattern.Const(IFTPattern.AnyArgument("const"))) {
                _, outRegister, _, args ->
                listOf(Instruction.InPlaceInstruction.MoveRI(outRegister, args["const"] as Constant)) // MOV  out,  const
            },
            InstructionPattern(
                IFTPattern.MemoryWrite(
                    IFTPattern.BinaryOperator(
                        IFTNode.Add::class,
                        IFTPattern.MemoryLabel(IFTPattern.AnyArgument("label"))
                    ),
                )
            ) {
                inRegisters, _, _, args ->
                listOf(
                    Instruction.InPlaceInstruction.MoveMR(
                        Addressing.Base(inRegisters[0], Addressing.MemoryAddress.Label(args["label"] as String)),
                        inRegisters[1]
                    )
                ) // MOV  [label + reg0], reg1
            },
            InstructionPattern(IFTPattern.MemoryWrite()) {
                inRegisters, _, _, _ ->
                listOf(Instruction.InPlaceInstruction.MoveMR(Addressing.Base(inRegisters[0]), inRegisters[1])) // MOV  [reg0], reg1
            },
            InstructionPattern(
                IFTPattern.RegisterWrite(
                    IFTPattern.AnyArgument("reg"),
                    IFTPattern.Const(IFTPattern.AnyArgument("const"))
                )
            ) {
                _, _, _, args ->
                listOf(Instruction.InPlaceInstruction.MoveRI(args["reg"] as Register, args["const"] as Constant)) // MOV  reg,  const
            },
            InstructionPattern(
                IFTPattern.RegisterWrite(
                    IFTPattern.AnyArgument("reg_dest"),
                    IFTPattern.RegisterRead(IFTPattern.AnyArgument("reg_src"))
                )
            ) {
                _, _, _, args ->
                listOf(Instruction.InPlaceInstruction.MoveRR(args["reg_dest"] as Register, args["reg_src"] as Register)) // MOV  reg_dest,  reg_src
            },
            InstructionPattern(IFTPattern.RegisterWrite(IFTPattern.AnyArgument("reg"), IFTPattern.StackPop())) {
                _, _, _, args ->
                listOf(Instruction.InPlaceInstruction.PopR(args["reg"] as Register)) // POP  reg
            },
            InstructionPattern(IFTPattern.RegisterWrite(IFTPattern.AnyArgument("reg"))) {
                inRegisters, _, _, args ->
                listOf(Instruction.InPlaceInstruction.MoveRR(args["reg"] as Register, inRegisters[0])) // MOV  reg,  reg0
            },
            InstructionPattern(IFTPattern.StackPush(IFTPattern.RegisterRead(IFTPattern.AnyArgument("reg")))) {
                _, _, _, args ->
                listOf(
                    Instruction.InPlaceInstruction.PushR(args["reg"] as Register) // PUSH reg
                )
            },
            InstructionPattern(IFTPattern.StackPush()) {
                inRegisters, _, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.PushR(inRegisters[0]) // PUSH reg0
                )
            },
            InstructionPattern(IFTPattern.StackPop()) {
                _, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.PopR(outRegister) // POP  out
                )
            },
            InstructionPattern(IFTPattern.JumpToRegister(IFTPattern.AnyArgument("reg"))) {
                _, _, _, args ->
                listOf(
                    Instruction.TerminalInstruction.JmpR(args["reg"] as Register)
                )
            },
            InstructionPattern(
                IFTPattern.Call(
                    IFTPattern.MemoryLabel(IFTPattern.AnyArgument("label")),
                    IFTPattern.AnyArgument("usedRegs"),
                    IFTPattern.AnyArgument("definedRegs")
                )
            ) {
                _, _, _, args ->
                @Suppress("UNCHECKED_CAST")
                listOf(
                    Instruction.InPlaceInstruction.CallL(
                        args["label"] as String,
                        args["usedRegs"] as Collection<Register>,
                        args["definedRegs"] as Collection<Register>
                    ) // CALL label
                )
            },
            InstructionPattern(
                IFTPattern.Call(
                    IFTPattern.AnyNode(),
                    IFTPattern.AnyArgument("usedRegs"),
                    IFTPattern.AnyArgument("definedRegs")
                )
            ) {
                inRegisters, _, _, args ->
                @Suppress("UNCHECKED_CAST")
                listOf(
                    Instruction.InPlaceInstruction.CallR(
                        inRegisters[0],
                        args["usedRegs"] as Collection<Register> + setOf(inRegisters[0]),
                        args["definedRegs"] as Collection<Register>
                    ) // CALL reg0
                )
            },
            InstructionPattern(IFTPattern.Return(IFTPattern.AnyArgument("usedRegs"))) {
                _, _, _, args ->
                @Suppress("UNCHECKED_CAST")
                listOf(
                    Instruction.TerminalInstruction.Ret(args["usedRegs"] as Collection<Register>)
                )
            },
            InstructionPattern(IFTPattern.UnaryOperator(IFTNode.Negation::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.MoveRR(outRegister, inRegisters[0]), //   MOV  out,  reg0
                    Instruction.InPlaceInstruction.NegR(outRegister) //                      NEG  out
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.Add::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.AddRR(inRegisters[0], inRegisters[1]), // ADD  reg0, reg1
                    Instruction.InPlaceInstruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.Subtract::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.SubRR(inRegisters[0], inRegisters[1]), // SUB  reg0, reg1
                    Instruction.InPlaceInstruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.Multiply::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.MulRR(inRegisters[0], inRegisters[1]), // IMUL reg0, reg1
                    Instruction.InPlaceInstruction.MoveRR(outRegister, inRegisters[0]), //     MOV  out,  reg0
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.Divide::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.MoveRR(Register.RAX, inRegisters[0]), //  MOV  rax,  reg0
                    Instruction.InPlaceInstruction.Cqo(), //                                 CQO              ; sign extend rax into rdx
                    Instruction.InPlaceInstruction.DivR(inRegisters[1]), //                  IDIV reg1
                    Instruction.InPlaceInstruction.MoveRR(outRegister, Register.RAX), //     MOV  out,  rax
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.Modulo::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.MoveRR(Register.RAX, inRegisters[0]), //  MOV  rax,  reg0
                    Instruction.InPlaceInstruction.Cqo(), //                                 CQO              ; sign extend rax into rdx
                    Instruction.InPlaceInstruction.DivR(inRegisters[1]), //                  IDIV reg1
                    Instruction.InPlaceInstruction.MoveRR(outRegister, Register.RDX), //     MOV  out,  rdx
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.BitAnd::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.AndRR(inRegisters[0], inRegisters[1]), // AND  reg0, reg1
                    Instruction.InPlaceInstruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.BitOr::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.OrRR(inRegisters[0], inRegisters[1]), //  OR   reg0, reg1
                    Instruction.InPlaceInstruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.BitXor::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.XorRR(inRegisters[0], inRegisters[1]), // XOR  reg0, reg1
                    Instruction.InPlaceInstruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(IFTPattern.UnaryOperator(IFTNode.BitNegation::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.NotR(inRegisters[0]), //                  NOT  reg0
                    Instruction.InPlaceInstruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.BitShiftLeft::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.MoveRR(Register.RCX, inRegisters[1]), //  MOV  rcx,  reg1
                    Instruction.InPlaceInstruction.ShiftLeftR(inRegisters[0]), //            SAL  reg0, CL
                    Instruction.InPlaceInstruction.MoveRR(outRegister, inRegisters[0]), //   MOV  out,  reg0
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.BitShiftRight::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.MoveRR(Register.RCX, inRegisters[1]), //  MOV  rcx,  reg1
                    Instruction.InPlaceInstruction.ShiftRightR(inRegisters[0]), //           SAR  reg0, CL
                    Instruction.InPlaceInstruction.MoveRR(outRegister, inRegisters[0]), //   MOV  out,  reg0
                )
            },
            InstructionPattern(IFTPattern.UnaryOperator(IFTNode.LogicalNegation::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.MoveRI(outRegister, 1L), //               MOV  out,  1
                    Instruction.InPlaceInstruction.XorRR(outRegister, inRegisters[0]) //     XOR  out,  reg0
                )
            },
            InstructionPattern(IFTPattern.UnaryOperator(IFTNode.LogicalNegation::class), setOf(Context.CONDITIONAL)) {
                inRegisters, _, _, args ->
                listOf(
                    Instruction.InPlaceInstruction.TestRR(inRegisters[0], inRegisters[0]), //       TEST reg0, reg0
                    if (args["invert"] as Boolean)
                        Instruction.ConditionalJumpInstruction.JmpNZ(args["target"] as String) //    JNZ   target        ; inverted,  but we have negation, so this cancels out
                    else
                        Instruction.ConditionalJumpInstruction.JmpZ(args["target"] as String) //   JZ  target
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.LogicalXor::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.XorRR(inRegisters[0], inRegisters[1]), //        XOR  reg0, reg1
                    Instruction.InPlaceInstruction.MoveRR(outRegister, inRegisters[0]) //           MOV  out,  reg0
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.LogicalXor::class), setOf(Context.CONDITIONAL)) {
                inRegisters, _, _, args ->
                listOf(
                    Instruction.InPlaceInstruction.XorRR(inRegisters[0], inRegisters[1]), //        XOR  reg0, reg1
                    if (args["invert"] as Boolean)
                        Instruction.ConditionalJumpInstruction.JmpZ(args["target"] as String) //    JZ   target        ; inverted
                    else
                        Instruction.ConditionalJumpInstruction.JmpNZ(args["target"] as String) //   JNZ  target
                )
            },
            InstructionPattern(
                IFTPattern.FirstOf(
                    listOf(
                        IFTPattern.BinaryOperator(IFTNode.Equals::class),
                        IFTPattern.BinaryOperator(IFTNode.LogicalIff::class)
                    )
                )
            ) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.XorRR(outRegister, outRegister), //              XOR   out,  out    ; reset upper bits to 0
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP   reg0, reg1
                    Instruction.InPlaceInstruction.SetEqR(outRegister) //                           SETE  out
                )
            },
            InstructionPattern(
                IFTPattern.FirstOf(
                    listOf(
                        IFTPattern.BinaryOperator(IFTNode.Equals::class),
                        IFTPattern.BinaryOperator(IFTNode.LogicalIff::class)
                    )
                ),
                setOf(Context.CONDITIONAL)
            ) {
                inRegisters, _, _, args ->
                listOf(
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP  reg0, reg1
                    if (args["invert"] as Boolean)
                        Instruction.ConditionalJumpInstruction.JmpNEq(args["target"] as String) //  JNE  target        ; inverted
                    else
                        Instruction.ConditionalJumpInstruction.JmpEq(args["target"] as String) //   JE   target
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.NotEquals::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.XorRR(outRegister, outRegister), //              XOR   out,  out     ; reset upper bits to 0
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP   reg0, reg1
                    Instruction.InPlaceInstruction.SetNeqR(outRegister) //                          SETNE out
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.NotEquals::class), setOf(Context.CONDITIONAL)) {
                inRegisters, _, _, args ->
                listOf(
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP  reg0, reg1
                    if (args["invert"] as Boolean)
                        Instruction.ConditionalJumpInstruction.JmpEq(args["target"] as String) //   JE  target         ; inverted
                    else
                        Instruction.ConditionalJumpInstruction.JmpNEq(args["target"] as String) //  JNE   target
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.LessThan::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.XorRR(outRegister, outRegister), //              XOR   out,  out     ; reset upper bits to 0
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP   reg0, reg1
                    Instruction.InPlaceInstruction.SetLtR(outRegister) //                           SETL  out
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.LessThan::class), setOf(Context.CONDITIONAL)) {
                inRegisters, _, _, args ->
                listOf(
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP  reg0, reg1
                    if (args["invert"] as Boolean)
                        Instruction.ConditionalJumpInstruction.JmpGtEq(args["target"] as String) // JGE target         ; inverted
                    else
                        Instruction.ConditionalJumpInstruction.JmpLt(args["target"] as String) //   JL  target
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.LessThanOrEquals::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.XorRR(outRegister, outRegister), //              XOR   out,  out    ; reset upper bits to 0
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP   reg0, reg1
                    Instruction.InPlaceInstruction.SetLtEqR(outRegister) //                         SETLE out
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.LessThanOrEquals::class), setOf(Context.CONDITIONAL)) {
                inRegisters, _, _, args ->
                listOf(
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP  reg0, reg1
                    if (args["invert"] as Boolean)
                        Instruction.ConditionalJumpInstruction.JmpGt(args["target"] as String) //   JG  target         ; inverted
                    else
                        Instruction.ConditionalJumpInstruction.JmpLtEq(args["target"] as String) // JLE target
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.GreaterThan::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.XorRR(outRegister, outRegister), //              XOR   out,  out    ; reset upper bits to 0
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP   reg0, reg1
                    Instruction.InPlaceInstruction.SetGtR(outRegister) //                           SETG  out
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.GreaterThan::class), setOf(Context.CONDITIONAL)) {
                inRegisters, _, _, args ->
                listOf(
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP  reg0, reg1
                    if (args["invert"] as Boolean)
                        Instruction.ConditionalJumpInstruction.JmpLtEq(args["target"] as String) // JLE target         ; inverted
                    else
                        Instruction.ConditionalJumpInstruction.JmpGt(args["target"] as String) //   JG  target
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.GreaterThanOrEquals::class)) {
                inRegisters, outRegister, _, _ ->
                listOf(
                    Instruction.InPlaceInstruction.XorRR(outRegister, outRegister), //              XOR   out,  out    ; reset upper bits to 0
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP   reg0, reg1
                    Instruction.InPlaceInstruction.SetGtEqR(outRegister) //                         SETGE out
                )
            },
            InstructionPattern(IFTPattern.BinaryOperator(IFTNode.GreaterThanOrEquals::class), setOf(Context.CONDITIONAL)) {
                inRegisters, _, _, args ->
                listOf(
                    Instruction.InPlaceInstruction.CmpRR(inRegisters[0], inRegisters[1]), //        CMP  reg0, reg1
                    if (args["invert"] as Boolean)
                        Instruction.ConditionalJumpInstruction.JmpLt(args["target"] as String) //   JL   target        ; inverted
                    else
                        Instruction.ConditionalJumpInstruction.JmpGtEq(args["target"] as String) // JGE  target
                )
            },
            InstructionPattern(IFTPattern.MemoryRead(), setOf(Context.CONDITIONAL)) {
                inRegisters, _, _, args ->
                val reg = Register() // temporary register
                listOf(
                    Instruction.InPlaceInstruction.MoveRM(reg, Addressing.Base(inRegisters[0])), // MOV  reg,  [reg0]
                    Instruction.InPlaceInstruction.TestRR(reg, reg), //                             TEST reg0, reg0
                    if (args["invert"] as Boolean)
                        Instruction.ConditionalJumpInstruction.JmpZ(args["target"] as String) //    JZ   target        ; inverted
                    else
                        Instruction.ConditionalJumpInstruction.JmpNZ(args["target"] as String) //   JNZ  target
                )
            },
            InstructionPattern(IFTPattern.RegisterRead(IFTPattern.AnyArgument("reg")), setOf(Context.CONDITIONAL)) {
                _, _, _, args ->
                val reg = args["reg"] as Register
                listOf(
                    Instruction.InPlaceInstruction.TestRR(reg, reg), //                             TEST  reg, reg
                    if (args["invert"] as Boolean)
                        Instruction.ConditionalJumpInstruction.JmpZ(args["target"] as String) //    JZ   target        ; inverted
                    else
                        Instruction.ConditionalJumpInstruction.JmpNZ(args["target"] as String) //   JNZ  target
                )
            },
            InstructionPattern(IFTPattern.Const(IFTPattern.AnyArgument("const")), setOf(Context.CONDITIONAL)) {
                _, _, _, args ->
                val reg = Register() // temporary register
                listOf(
                    Instruction.InPlaceInstruction.MoveRI(reg, args["const"] as Constant), //       MOV  reg, const
                    Instruction.InPlaceInstruction.TestRR(reg, reg), //                             TEST reg, reg
                    if (args["invert"] as Boolean)
                        Instruction.ConditionalJumpInstruction.JmpZ(args["target"] as String) //    JZ   target        ; inverted
                    else
                        Instruction.ConditionalJumpInstruction.JmpNZ(args["target"] as String) //   JNZ  target
                )
            },
        )
    }
}
