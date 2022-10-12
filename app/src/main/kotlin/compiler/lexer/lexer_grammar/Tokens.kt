package compiler.lexer.lexer_grammar

import compiler.lexer.regex.Regex

class Tokens {

    fun getTokens(): List<Regex> {
        val list = mutableListOf<String>()
        // TODO: fill the list
        return list.map { RegexParser().parseStringToRegex(it) }
    }
}
