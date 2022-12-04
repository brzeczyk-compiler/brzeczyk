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
            InstructionPattern(Pattern.MemoryAddress(Pattern.AnyArgument("label"))) {
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
            InstructionPattern(Pattern.Return()) { _, _, _ -> listOf(Instruction.Ret()) }, //                              RET
            InstructionPattern(Pattern.UnaryOperator(IntermediateFormTreeNode.Negation::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.MoveRI(outRegister, 0L), //              MOV  out,  0
                    Instruction.Sub(outRegister, inRegisters[0]) //      SUB  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Add::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Add(inRegisters[0], inRegisters[1]), //  ADD  reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //   MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Subtract::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Sub(inRegisters[0], inRegisters[1]), //  SUB  reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //   MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Multiply::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.PushR(Register.RDX), //                  PUSH rdx           ; save the result registers
                    Instruction.PushR(Register.RAX), //                  PUSH rax
                    Instruction.MoveRR(Register.RAX, inRegisters[0]), // MOV  rax,  reg0
                    Instruction.Mul(inRegisters[1]), //                  IMUL reg1
                    Instruction.MoveRR(outRegister, Register.RAX), //    MOV  out,  rax     ; out can't be rax or rdx
                    Instruction.PopR(Register.RAX), //                   POP  rax
                    Instruction.PopR(Register.RDX) //                    POP  rdx
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Divide::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.PushR(Register.RDX), //                  PUSH rdx           ; save the result registers
                    Instruction.PushR(Register.RAX), //                  PUSH rax
                    Instruction.MoveRR(Register.RAX, inRegisters[0]), // MOV  rax,  reg0
                    Instruction.Div(inRegisters[1]), //                  IDIV reg1
                    Instruction.MoveRR(outRegister, Register.RAX), //    MOV  out,  rax     ; out can't be rax or rdx
                    Instruction.PopR(Register.RAX), //                   POP  rax
                    Instruction.PopR(Register.RDX) //                    POP  rdx
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Modulo::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.PushR(Register.RDX), //                  PUSH rdx           ; save the result registers
                    Instruction.PushR(Register.RAX), //                  PUSH rax
                    Instruction.MoveRR(Register.RAX, inRegisters[0]), // MOV  rax,  reg0
                    Instruction.Div(inRegisters[1]), //                  IDIV reg1
                    Instruction.MoveRR(outRegister, Register.RDX), //    MOV  out,  rdx     ; out can't be rax or rdx
                    Instruction.PopR(Register.RAX), //                   POP  rax
                    Instruction.PopR(Register.RDX) //                    POP  rdx
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.BitAnd::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.And(inRegisters[0], inRegisters[1]), //  AND  reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //   MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.BitOr::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Or(inRegisters[0], inRegisters[1]), //   AND  reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //   MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.BitXor::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Xor(inRegisters[0], inRegisters[1]), //  XOR  reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //   MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.UnaryOperator(IntermediateFormTreeNode.BitNegation::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.BitNeg(inRegisters[0]), //               NEG  reg0
                    Instruction.MoveRR(outRegister, inRegisters[0]) //   MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.BitShiftLeft::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.PushR(Register.RCX), //                  PUSH rcx           ; save rcx
                    Instruction.MoveRR(Register.RCX, inRegisters[1]), // MOV  rcx,  reg1
                    Instruction.ShiftLeft(inRegisters[0]), //            SHL  reg0, CL
                    Instruction.MoveRR(outRegister, inRegisters[0]), //  MOV  out,  reg0
                    Instruction.PopR(Register.RCX), //                   POP  rcx
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.BitShiftRight::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.PushR(Register.RCX), //                  PUSH rcx           ; save rcx
                    Instruction.MoveRR(Register.RCX, inRegisters[1]), // MOV  rcx,  reg1
                    Instruction.ShiftRight(inRegisters[0]), //           SHR  reg0, CL
                    Instruction.MoveRR(outRegister, inRegisters[0]), //  MOV  out,  reg0
                    Instruction.PopR(Register.RCX), //                   POP  rcx
                )
            },
            InstructionPattern(Pattern.UnaryOperator(IntermediateFormTreeNode.LogicalNegation::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.MoveRI(outRegister, 1L), //              MOV  out,  1
                    Instruction.Xor(outRegister, inRegisters[0]) //      XOR  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.LogicalXor::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Xor(inRegisters[0], inRegisters[1]), //  XOR  reg0, reg1
                    Instruction.MoveRR(outRegister, inRegisters[0]) //   MOV  out,  reg0
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.LogicalIff::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Xor(outRegister, outRegister), //        XOR   out,  out    ; reset upper bits to 0
                    Instruction.Cmp(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetEq(outRegister) //                    SETE  out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.Equals::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Xor(outRegister, outRegister), //        XOR   out,  out     ; reset upper bits to 0
                    Instruction.Cmp(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetEq(outRegister) //                    SETE  out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.NotEquals::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Xor(outRegister, outRegister), //        XOR   out,  out     ; reset upper bits to 0
                    Instruction.Cmp(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetNeq(outRegister) //                   SETNE out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.LessThan::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Xor(outRegister, outRegister), //        XOR   out,  out     ; reset upper bits to 0
                    Instruction.Cmp(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetLt(outRegister) //                    SETL  out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.LessThanOrEquals::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Xor(outRegister, outRegister), //        XOR   out,  out     ; reset upper bits to 0
                    Instruction.Cmp(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetLteq(outRegister) //                  SETLE out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.GreaterThan::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Xor(outRegister, outRegister), //        XOR   out,  out     ; reset upper bits to 0
                    Instruction.Cmp(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetGt(outRegister) //                    SETG  out
                )
            },
            InstructionPattern(Pattern.BinaryOperator(IntermediateFormTreeNode.GreaterThanOrEquals::class)) {
                inRegisters, outRegister, _ ->
                listOf(
                    Instruction.Xor(outRegister, outRegister), //        XOR   out,  out     ; reset upper bits to 0
                    Instruction.Cmp(inRegisters[0], inRegisters[1]), //  CMP   reg0, reg1
                    Instruction.SetGteq(outRegister) //                  SETGE out
                )
            },
        )
    }
}
