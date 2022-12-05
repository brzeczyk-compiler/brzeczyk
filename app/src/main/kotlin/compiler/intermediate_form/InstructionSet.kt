package compiler.intermediate_form

object InstructionSet {
    data class InstructionPattern(
        val pattern: Pattern,
        val createInstructions: (List<Register>, Register, Map<String, Any>) -> List<Instruction>
    ) {
        fun match(node: IntermediateFormTreeNode):
            Pair<List<IntermediateFormTreeNode>, (List<Register>, Register) -> List<Instruction>>? {

            val (nodes, arguments) = pattern.match(node) ?: return null
            return Pair(nodes) { inRegisters, outRegister -> createInstructions(inRegisters, outRegister, arguments) }
        }
    }

    fun getInstructionSet(): List<InstructionPattern> {
        return listOf(
            InstructionPattern(Pattern.MemoryRead()) { // TODO match trees that represent more complicated addressing modes
                inRegisters, outRegister, _ ->
                listOf(Instruction.MoveRM(outRegister, Addressing.Base(inRegisters[0]))) // MOV  out,  [reg0]
            },
            InstructionPattern(Pattern.MemoryLabel(Pattern.AnyArgument("label"))) {
                _, outRegister, capture ->
                listOf(
                    Instruction.Lea( // LEA  out,  [label]
                        outRegister,
                        Addressing.Displacement(Addressing.MemoryAddress.Label(capture["label"] as String))
                    )
                )
            },
            InstructionPattern(Pattern.RegisterRead(Pattern.AnyArgument("reg"))) {
                _, outRegister, capture ->
                listOf(Instruction.MoveRR(outRegister, capture["reg"] as Register)) // MOV  out,  reg
            },
            InstructionPattern(Pattern.Const(Pattern.AnyArgument("const"))) {
                _, outRegister, capture ->
                listOf(Instruction.MoveRI(outRegister, capture["const"] as Long)) // MOV  out,  const
            },
            InstructionPattern(Pattern.MemoryWrite()) {
                inRegisters, _, _ ->
                listOf(Instruction.MoveMR(Addressing.Base(inRegisters[0]), inRegisters[1])) // MOV  [reg0], reg1
            },
            InstructionPattern(Pattern.RegisterWrite(Pattern.AnyArgument("reg"))) {
                inRegisters, _, capture ->
                listOf(Instruction.MoveRR(capture["reg"] as Register, inRegisters[0])) // MOV  reg,  reg0
            },
            InstructionPattern(Pattern.StackPush()) { inRegisters, _, _ -> listOf(Instruction.PushR(inRegisters[0])) }, // PUSH reg0
            InstructionPattern(Pattern.StackPop()) { _, outRegister, _ -> listOf(Instruction.PopR(outRegister)) }, //      POP  out
            InstructionPattern(Pattern.Call()) { inRegisters, _, _ -> listOf(Instruction.CallR(inRegisters[0])) }, //      CALL reg0
            InstructionPattern(Pattern.UnaryOperator(IntermediateFormTreeNode.Negation::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.MoveRR(outRegister, inRegisters[0]), //   MOV  out,  reg0
                    Instruction.NegR(outRegister) //                      NEG  out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Add::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.AddRR(inRegisters[0], inRegisters[1]), // ADD  reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Subtract::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.SubRR(inRegisters[0], inRegisters[1]), // SUB  reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Multiply::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.MulRR(inRegisters[0], inRegisters[1]), // IMUL reg0, reg1
                    Instruction.MoveRR(outRegister, Register.RAX), //     MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Divide::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.MoveRR(Register.RAX, inRegisters[0]), //  MOV  rax,  reg0
                    Instruction.DivR(inRegisters[1]), //                  IDIV reg1
                    Instruction.MoveRR(outRegister, Register.RAX), //     MOV  out,  rax
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Modulo::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.MoveRR(Register.RAX, inRegisters[0]), //  MOV  rax,  reg0
                    Instruction.DivR(inRegisters[1]), //                  IDIV reg1
                    Instruction.MoveRR(outRegister, Register.RDX), //     MOV  out,  rdx
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.BitAnd::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.AndRR(inRegisters[0], inRegisters[1]), // AND  reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.BitOr::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.OrRR(inRegisters[0], inRegisters[1]), //  OR   reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.BitXor::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.XorRR(inRegisters[0], inRegisters[1]), // XOR  reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.UnaryOperator(IntermediateFormTreeNode.BitNegation::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.NotR(inRegisters[0]), //                  NOT  reg0
                    Instruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.BitShiftLeft::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.MoveRR(Register.RCX, inRegisters[1]), //  MOV  rcx,  reg1
                    Instruction.ShiftLeftR(inRegisters[0]), //            SAL  reg0, CL
                    Instruction.MoveRR(outRegister, inRegisters[0]), //   MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.BitShiftRight::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.MoveRR(Register.RCX, inRegisters[1]), //  MOV  rcx,  reg1
                    Instruction.ShiftRightR(inRegisters[0]), //           SAR  reg0, CL
                    Instruction.MoveRR(outRegister, inRegisters[0]), //   MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.UnaryOperator(IntermediateFormTreeNode.LogicalNegation::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.MoveRI(outRegister, 1L), //               MOV  out,  1
                    Instruction.XorRR(outRegister, inRegisters[0]) //     XOR  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.LogicalXor::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.XorRR(inRegisters[0], inRegisters[1]), // XOR  reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //    MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.LogicalIff::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.XorRR(outRegister, outRegister), //       XOR   out,  out    ; reset upper bits to 0
                    Instruction.CmpR(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetEqR(outRegister) //                    SETE  out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Equals::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.XorRR(outRegister, outRegister), //       XOR   out,  out     ; reset upper bits to 0
                    Instruction.CmpR(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetEqR(outRegister) //                    SETE  out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.NotEquals::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.XorRR(outRegister, outRegister), //       XOR   out,  out     ; reset upper bits to 0
                    Instruction.CmpR(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetNeqR(outRegister) //                   SETNE out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.LessThan::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.XorRR(outRegister, outRegister), //       XOR   out,  out     ; reset upper bits to 0
                    Instruction.CmpR(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetLtR(outRegister) //                    SETL  out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.LessThanOrEquals::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.XorRR(outRegister, outRegister), //       XOR   out,  out     ; reset upper bits to 0
                    Instruction.CmpR(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetLtEqR(outRegister) //                  SETLE out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.GreaterThan::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.XorRR(outRegister, outRegister), //       XOR   out,  out     ; reset upper bits to 0
                    Instruction.CmpR(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetGtR(outRegister) //                    SETG  out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.GreaterThanOrEquals::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.XorRR(outRegister, outRegister), //       XOR   out,  out     ; reset upper bits to 0
                    Instruction.CmpR(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetGtEqR(outRegister) //                  SETGE out
                )
            },
        )
    }
}
