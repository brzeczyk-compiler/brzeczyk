package compiler.lexer.regex

import kotlin.test.Test
import kotlin.test.assertEquals

private val CONCAT_AB = RegexFactory.createConcat(RegexFactory.createAtomic(setOf('a')), RegexFactory.createAtomic(setOf('b')))
private val CONCAT_AC = RegexFactory.createConcat(RegexFactory.createAtomic(setOf('a')), RegexFactory.createAtomic(setOf('c')))
private val CONCAT_BC = RegexFactory.createConcat(RegexFactory.createAtomic(setOf('b')), RegexFactory.createAtomic(setOf('c')))
private val CONCAT_CD = RegexFactory.createConcat(RegexFactory.createAtomic(setOf('c')), RegexFactory.createAtomic(setOf('d')))
private val EMPTY = RegexFactory.createEmpty()
private val EPSILON = RegexFactory.createEpsilon()

class RegexFactoryTest {
    @Test fun testUnionNormalizeBasicRules() {
        assertEquals(RegexFactory.createUnion(CONCAT_AB, CONCAT_AB), CONCAT_AB)
        assertEquals(RegexFactory.createUnion(CONCAT_AB, CONCAT_AC), RegexFactory.createUnion(CONCAT_AC, CONCAT_AB))

        val union1 = RegexFactory.createUnion(CONCAT_AB, CONCAT_AC)
        val union2 = RegexFactory.createUnion(CONCAT_AC, CONCAT_BC)
        assertEquals(RegexFactory.createUnion(union1, CONCAT_BC), RegexFactory.createUnion(CONCAT_AB, union2))
        assertEquals(RegexFactory.createUnion(CONCAT_AB, EMPTY), EMPTY)
        assertEquals(RegexFactory.createUnion(EMPTY, CONCAT_AB), EMPTY)
    }

    @Test fun testUnionNormalizeOrdering() {
        var unionLeftOrdered = RegexFactory.createUnion(CONCAT_AB, CONCAT_AC)
        unionLeftOrdered = RegexFactory.createUnion(unionLeftOrdered, CONCAT_BC)
        unionLeftOrdered = RegexFactory.createUnion(unionLeftOrdered, CONCAT_CD)
        var unionLeftReversed = RegexFactory.createUnion(CONCAT_CD, CONCAT_BC)
        unionLeftReversed = RegexFactory.createUnion(unionLeftReversed, CONCAT_AC)
        unionLeftReversed = RegexFactory.createUnion(unionLeftReversed, CONCAT_AB)
        var unionLeftRandom = RegexFactory.createUnion(CONCAT_AC, CONCAT_BC)
        unionLeftRandom = RegexFactory.createUnion(unionLeftRandom, CONCAT_AB)
        unionLeftRandom = RegexFactory.createUnion(unionLeftRandom, CONCAT_CD)

        var unionRightOrdered = RegexFactory.createUnion(CONCAT_AC, CONCAT_AB)
        unionRightOrdered = RegexFactory.createUnion(CONCAT_BC, unionRightOrdered)
        unionRightOrdered = RegexFactory.createUnion(CONCAT_CD, unionRightOrdered)
        var unionRightReversed = RegexFactory.createUnion(CONCAT_BC, CONCAT_CD)
        unionRightReversed = RegexFactory.createUnion(CONCAT_AC, unionRightReversed)
        unionRightReversed = RegexFactory.createUnion(CONCAT_AB, unionRightReversed)
        var unionRightRandom = RegexFactory.createUnion(CONCAT_BC, CONCAT_AC)
        unionRightRandom = RegexFactory.createUnion(CONCAT_AB, unionRightRandom)
        unionRightRandom = RegexFactory.createUnion(CONCAT_CD, unionRightRandom)

        var unionUnorganized1 = RegexFactory.createUnion(CONCAT_BC, RegexFactory.createUnion(CONCAT_CD, CONCAT_AB))
        unionUnorganized1 = RegexFactory.createUnion(unionUnorganized1, CONCAT_AC)
        var unionUnorganized2 = RegexFactory.createUnion(RegexFactory.createUnion(CONCAT_AC, CONCAT_AB), RegexFactory.createUnion(CONCAT_CD, CONCAT_BC))
        var unionUnorganized3 = RegexFactory.createUnion(RegexFactory.createUnion(CONCAT_AB, CONCAT_BC), RegexFactory.createUnion(CONCAT_AC, CONCAT_CD))
        var unionUnorganized4 = RegexFactory.createUnion(RegexFactory.createUnion(CONCAT_CD, CONCAT_AB), CONCAT_AC)
        unionUnorganized4 = RegexFactory.createUnion(CONCAT_BC, unionUnorganized4)

        assertEquals(unionLeftOrdered, unionLeftReversed)
        assertEquals(unionLeftReversed, unionLeftRandom)
        println(unionRightOrdered)
        assertEquals(unionLeftRandom, unionRightOrdered)
        assertEquals(unionRightOrdered, unionRightReversed)
        assertEquals(unionRightReversed, unionRightRandom)
        assertEquals(unionRightRandom, unionUnorganized1)
        assertEquals(unionUnorganized1, unionUnorganized2)
        assertEquals(unionUnorganized2, unionUnorganized3)
        assertEquals(unionUnorganized3, unionUnorganized4)
        assertEquals(unionUnorganized4, Regex.Union(Regex.Union(Regex.Union(CONCAT_AB, CONCAT_AC), CONCAT_BC), CONCAT_CD))
    }

    @Test fun testUnionMergeAtomics() {
        val atomicAb = RegexFactory.createAtomic(setOf('a', 'b'))
        val atomicBc = RegexFactory.createAtomic(setOf('c', 'b'))
        val atomicAbc = RegexFactory.createAtomic(setOf('c', 'a', 'b'))
        assertEquals(RegexFactory.createUnion(atomicAb, atomicBc), atomicAbc)

        val atomicAbConcatAb = RegexFactory.createUnion(atomicAb, CONCAT_AB)
        val atomicBcConcatBc = RegexFactory.createUnion(atomicBc, CONCAT_BC)
        val all1 = RegexFactory.createUnion(atomicAbConcatAb, atomicBcConcatBc)
        val concats = RegexFactory.createUnion(CONCAT_BC, CONCAT_AB)
        val all2 = RegexFactory.createUnion(atomicAb, RegexFactory.createUnion(atomicBc, concats))
        val allAtomicsMerged = RegexFactory.createUnion(atomicAbc, concats)

        assertEquals(all1, all2)
        assertEquals(all2, allAtomicsMerged)
    }

    @Test fun testConcatNormalize() {
        val concat1 = RegexFactory.createConcat(CONCAT_AB, CONCAT_AC)
        val concat2 = RegexFactory.createConcat(CONCAT_AC, CONCAT_BC)
        assertEquals(RegexFactory.createConcat(concat1, CONCAT_BC), RegexFactory.createConcat(CONCAT_AB, concat2))
        assertEquals(RegexFactory.createConcat(CONCAT_AB, EMPTY), EMPTY)
        assertEquals(RegexFactory.createConcat(EMPTY, CONCAT_AB), EMPTY)
        assertEquals(RegexFactory.createConcat(CONCAT_AB, EPSILON), CONCAT_AB)
        assertEquals(RegexFactory.createConcat(EPSILON, CONCAT_AB), CONCAT_AB)

        var concatLeft = RegexFactory.createConcat(CONCAT_AB, CONCAT_AC)
        concatLeft = RegexFactory.createConcat(concatLeft, CONCAT_BC)
        concatLeft = RegexFactory.createConcat(concatLeft, CONCAT_CD)
        var concatRight = RegexFactory.createConcat(CONCAT_BC, CONCAT_CD)
        concatRight = RegexFactory.createConcat(CONCAT_AC, concatRight)
        concatRight = RegexFactory.createConcat(CONCAT_AB, concatRight)
        val concatSymmetrical = RegexFactory.createConcat(
            RegexFactory.createConcat(CONCAT_AB, CONCAT_AC),
            RegexFactory.createConcat(CONCAT_BC, CONCAT_CD)
        )
        var concatMixed = RegexFactory.createConcat(CONCAT_AC, CONCAT_BC)
        concatMixed = RegexFactory.createConcat(CONCAT_AB, concatMixed)
        concatMixed = RegexFactory.createConcat(concatMixed, CONCAT_CD)

        assertEquals(concatLeft, concatRight)
        assertEquals(concatRight, concatSymmetrical)
        assertEquals(concatSymmetrical, concatMixed)
    }
    @Test fun testStarNormalize() {
        val starAtomicAb = RegexFactory.createStar(CONCAT_AB)
        assertEquals(RegexFactory.createStar(starAtomicAb), starAtomicAb)
        assertEquals(RegexFactory.createStar(RegexFactory.createStar(starAtomicAb)), starAtomicAb)
        assertEquals(RegexFactory.createStar(EMPTY), EPSILON)
        assertEquals(RegexFactory.createStar(EPSILON), EPSILON)
    }
}
