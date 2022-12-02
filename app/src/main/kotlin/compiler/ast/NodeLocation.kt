package compiler.ast

import compiler.lexer.Location
import compiler.parser.ParseTree

data class NodeLocation(val start: Location, val end: Location)

fun <S : Comparable<S>> toLocation(parseTree: ParseTree<S>) = NodeLocation(parseTree.start, parseTree.end)

fun <S : Comparable<S>> toLocation(parseTreeStart: ParseTree<S>, parseTreeEnd: ParseTree<S>) = NodeLocation(parseTreeStart.start, parseTreeEnd.end)
