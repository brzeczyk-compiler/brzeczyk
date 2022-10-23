package compiler.parser.analysis

import compiler.common.dfa.DfaWalk
import compiler.common.dfa.state_dfa.Dfa
import compiler.common.dfa.state_dfa.DfaState
import compiler.parser.grammar.Production

typealias R = Production<GrammarSymbol>
typealias GrammarSymbol = String
typealias DfaStateName = String

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

    class TestDfaState(
        private val name: DfaStateName,
        private val transitionFunction: Map<Pair<DfaStateName, GrammarSymbol>, DfaStateName>
    ) : DfaState<GrammarSymbol, R> {

        override val result = if (name.startsWith("acc")) R() else null
        override val possibleSteps: Map<GrammarSymbol, TestDfaState>
            get() = transitionFunction.filter { it.key.first == name }.entries
                .associate { it.key.second to if (it.value == name) this else TestDfaState(it.value, transitionFunction) }

        override fun equals(other: Any?): Boolean = other is TestDfaState && name == other.name
        override fun hashCode(): Int = name.hashCode()
    }

    object DfaFactory {

        fun getTrivialDfa(): TestDfa = TestDfa(TestDfaState("accStartDfa", mapOf()), mapOf())

        fun createDfa(
            startState: DfaStateName,
            dfaStates: List<DfaStateName>,
            transitionFunction: Map<Pair<DfaStateName, GrammarSymbol>, DfaStateName>
        ): TestDfa {
            val dfaStatesObjects = dfaStates.associateWith { TestDfaState(it, transitionFunction) }
            return TestDfa(
                dfaStatesObjects[startState]!!,
                transitionFunction.entries.associate { Pair(dfaStatesObjects[it.key.first]!!, it.key.second) to dfaStatesObjects[it.value]!! }
            )
        }
    }
}
