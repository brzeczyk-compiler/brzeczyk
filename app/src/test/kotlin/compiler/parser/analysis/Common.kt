package compiler.parser.analysis

import compiler.common.dfa.DfaWalk
import compiler.common.dfa.state_dfa.Dfa
import compiler.common.dfa.state_dfa.DfaState
import compiler.parser.grammar.Production

typealias R = Production<GrammarSymbol>
typealias GrammarSymbol = String

class GrammarAnalysisTest {

    class TestDfa(
        override val startState: TestDfaState,
        private val dfaDescription: Map<Pair<TestDfaState, GrammarSymbol>, TestDfaState>
    ) : Dfa<GrammarSymbol, R> {

        override fun newWalk(): DfaWalk<GrammarSymbol, R> = TestDfaWalk(startState, dfaDescription)
    }

    class TestDfaWalk(
        private var currentState: TestDfaState,
        private val dfaDescription: Map<Pair<TestDfaState, GrammarSymbol>, TestDfaState>
    ) : DfaWalk<GrammarSymbol, R> {

        override fun getAcceptingStateTypeOrNull(): R? = currentState.result

        override fun isDead(): Boolean = !dfaDescription.keys.map { it.first }.contains(currentState)

        override fun step(a: GrammarSymbol) {
            dfaDescription[Pair(currentState, a)]?.let { currentState = it }
        }
    }

    class TestDfaState(name: String) : DfaState<GrammarSymbol, R> {

        override val result = if (name.startsWith("acc")) R() else null
        override val possibleSteps: Map<GrammarSymbol, TestDfaState> = mapOf()
    }

    object DfaFactory {

        fun getTrivialDfa(): TestDfa = TestDfa(TestDfaState("accStartDfa"), mapOf())

        fun createDfa(
            startState: String,
            dfaStates: List<String>,
            transitionFunction: Map<Pair<String, GrammarSymbol>, String>
        ): TestDfa {
            val dfaStatesObjects = dfaStates.associateWith { TestDfaState(it) }
            return TestDfa(
                dfaStatesObjects[startState]!!,
                transitionFunction.entries.associate { Pair(dfaStatesObjects[it.key.first]!!, it.key.second) to dfaStatesObjects[it.value]!! }
            )
        }
    }
}
