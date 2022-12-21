package compiler.grammar

import compiler.dfa.Dfa
import compiler.dfa.DfaState
import compiler.dfa.DfaWalk
import compiler.regex.RegexFactory

typealias R = Production<GrammarSymbol>
typealias GrammarSymbol = String
typealias DfaStateName = String

private val dummyNotNullResult = R("", RegexFactory.createEmpty())

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

        override val result = if (name.startsWith("acc")) dummyNotNullResult else null
        override val possibleSteps: Map<GrammarSymbol, TestDfaState>
            get() = transitionFunction.filter { it.key.first == name }.entries
                .associate { it.key.second to if (it.value == name) this else TestDfaState(it.value, transitionFunction) }

        override fun equals(other: Any?): Boolean = other is TestDfaState && name == other.name
        override fun hashCode(): Int = name.hashCode()
    }

    object DfaFactory {

        fun getTrivialDfa(dfaUniqueName: String): TestDfa = TestDfa(TestDfaState("accStartDfa".plus(dfaUniqueName), mapOf()), mapOf())

        fun createDfa(
            startState: DfaStateName,
            dfaStates: List<DfaStateName>,
            transitionFunction: Map<Pair<DfaStateName, GrammarSymbol>, DfaStateName>,
            dfaUniqueName: String,
        ): TestDfa {
            val transitionFunctionProperNames = transitionFunction
                .entries.associate { Pair(it.key.first.plus(dfaUniqueName), it.key.second) to it.value.plus(dfaUniqueName) }
            val dfaStatesObjects = dfaStates.associateWith { TestDfaState(it.plus(dfaUniqueName), transitionFunctionProperNames) }
            return TestDfa(
                dfaStatesObjects[startState]!!,
                transitionFunction.entries.associate { Pair(dfaStatesObjects[it.key.first]!!, it.key.second) to dfaStatesObjects[it.value]!! }
            )
        }
    }
}
