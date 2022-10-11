package compiler.lexer.grammar

import compiler.lexer.regex.Regex

class Grammar {

    fun getGrammar(): List<Regex> {
        val list = mutableListOf<String>()
        // TODO: fill the list
        return list.map { MiniParser().parseStringToRegex(it) }
    }
}
