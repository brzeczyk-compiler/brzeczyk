package compiler.parser.grammar

import compiler.common.regex.RegexFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AutomatonGrammarTest {
    private val atomic1 = RegexFactory.createAtomic(setOf(1))
    private val atomic2 = RegexFactory.createAtomic(setOf(2))
    private val atomic3 = RegexFactory.createAtomic(setOf(3))

    private fun getSingleProductionGrammar(): Grammar<Int> {
        // start = 0
        // 0 -> 1 2
        return Grammar(0, listOf(Production(0, RegexFactory.createConcat(atomic1, atomic2))))
    }

    private fun getMultipleProductionsFromOneSymbolGrammar(): Grammar<Int> {
        // start = 2
        // 2 -> epsilon
        // 2 -> 1 2 2*
        val production1 = Production(2, RegexFactory.createEpsilon())
        val production2 = Production(2, RegexFactory.createConcat(RegexFactory.createConcat(atomic1, atomic2), RegexFactory.createStar(atomic2)))
        return Grammar(2, listOf(production1, production2))
    }

    private fun getMultipleProductionsGrammar(): Grammar<Int> {
        // start = 1
        // 1 -> 2
        // 1 -> 3 2*
        // 2 -> (1 2) *
        // 2 -> 3
        val production1 = Production(1, atomic2)
        val production2 = Production(1, RegexFactory.createConcat(atomic3, RegexFactory.createStar(atomic2)))
        val production3 = Production(2, RegexFactory.createStar(RegexFactory.createConcat(atomic1, atomic2)))
        val production4 = Production(2, atomic3)
        return Grammar(1, listOf(production1, production2, production3, production4))
    }

    private fun getAmbiguousGrammar(): Grammar<Int> {
        // start = 3
        // 3 -> 1* 2
        // 3 -> 1 2*
        val production1 = Production(3, RegexFactory.createConcat(RegexFactory.createStar(atomic1), atomic2))
        val production2 = Production(3, RegexFactory.createConcat(atomic1, RegexFactory.createStar(atomic2)))
        return Grammar(3, listOf(production1, production2))
    }

    @Test fun `test map has all lhs symbols`() {
        val grammar = getMultipleProductionsGrammar()
        val automatonGrammar = AutomatonGrammar.createFromGrammar(grammar)

        assertEquals(automatonGrammar.productions.keys, setOf(1, 2))
    }

    @Test fun `test fails on and only on ambiguous result`() {
        val grammar = getAmbiguousGrammar()
        val automatonGrammar = AutomatonGrammar.createFromGrammar(grammar)

        val walk = automatonGrammar.productions.getValue(3).newWalk()
        walk.getAcceptingStateTypeOrNull() // null
        walk.step(1)
        walk.getAcceptingStateTypeOrNull() // exactly one production
        walk.step(2)
        assertFails { walk.getAcceptingStateTypeOrNull() } // both productions
    }

    @Test fun `test accepts only correct word for single production`() {
        val grammar = getSingleProductionGrammar()
        val automatonGrammar = AutomatonGrammar.createFromGrammar(grammar)

        val walk = automatonGrammar.productions.getValue(0).newWalk()
        assertNull(walk.getAcceptingStateTypeOrNull())
        walk.step(1)
        assertNull(walk.getAcceptingStateTypeOrNull())
        walk.step(2)
        assertNotNull(walk.getAcceptingStateTypeOrNull())
        walk.step(1)
        assertNull(walk.getAcceptingStateTypeOrNull())
    }

    @Test fun `test accepts only correct word for multiple productions`() {
        val grammar = getMultipleProductionsFromOneSymbolGrammar()
        val automatonGrammar = AutomatonGrammar.createFromGrammar(grammar)

        val walk = automatonGrammar.productions.getValue(2).newWalk()
        assertNotNull(walk.getAcceptingStateTypeOrNull()) // correct empty word
        walk.step(1)
        assertNull(walk.getAcceptingStateTypeOrNull())
        walk.step(2)
        assertNotNull(walk.getAcceptingStateTypeOrNull()) // correct word 1 2
        walk.step(2)
        assertNotNull(walk.getAcceptingStateTypeOrNull()) // correct word 1 2 2
        walk.step(1)
        assertNull(walk.getAcceptingStateTypeOrNull())
    }

    @Test fun `test automatons are independent`() {
        val grammar = getMultipleProductionsGrammar()
        val automatonGrammar = AutomatonGrammar.createFromGrammar(grammar)

        val walk1 = automatonGrammar.productions.getValue(1).newWalk()
        val walk2 = automatonGrammar.productions.getValue(2).newWalk()

        assertNull(walk1.getAcceptingStateTypeOrNull())
        walk1.step(2)
        assertNotNull(walk1.getAcceptingStateTypeOrNull())

        assertNotNull(walk2.getAcceptingStateTypeOrNull())
        walk2.step(1)
        assertNull(walk2.getAcceptingStateTypeOrNull())
        walk2.step(2)
        assertNotNull(walk2.getAcceptingStateTypeOrNull())
    }
}
