package compiler.intermediate_form

class UnknownCharacter(message: String) : RuntimeException(message)

class UniqueIdentifierFactory() {
    private val knownConversionsToPlainASCII = mapOf(
        'ą' to 'a',
        'ć' to 'c',
        'ę' to 'e',
        'ł' to 'l',
        'ó' to 'o',
        'ś' to 's',
        'ź' to 'z',
        'ż' to 'z'
    )

    private val knownIdentifiers: MutableSet<String> = mutableSetOf()
    fun build(prefix: String?, curr: String): UniqueIdentifier {
        fun convertToPlainASCII(character: Char): Char {
            if (character.code in 0..127) return character
            if (character in knownConversionsToPlainASCII) return knownConversionsToPlainASCII[character]!!
            throw UnknownCharacter("Unknown character: $character. Only polish signs are allowed outside of the plain ASCII characters.")
        }
        val identifierBuilder = StringBuilder()
        prefix?.let {
            identifierBuilder.append(it)
            identifierBuilder.append('|')
        }
        curr.forEach { identifierBuilder.append(convertToPlainASCII(it)) }
        var identifier = identifierBuilder.toString()
        while (identifier in knownIdentifiers) identifier += "_"
        knownIdentifiers.add(identifier)
        return UniqueIdentifier(identifier)
    }
}

data class UniqueIdentifier internal constructor(val value: String)
