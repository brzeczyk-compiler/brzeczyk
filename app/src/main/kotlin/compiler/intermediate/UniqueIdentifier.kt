package compiler.intermediate

import compiler.intermediate.generators.DISPLAY_LABEL_IN_MEMORY
import compiler.intermediate.generators.GlobalVariableAccessGenerator

class IllegalCharacter(message: String) : RuntimeException(message)
class InconsistentFunctionNamingConvention(message: String) : RuntimeException(message)

const val MAIN_FUNCTION_IDENTIFIER = "główna"

class UniqueIdentifierFactory() {
    companion object {
        // it might be handy to change these values with ease
        val functionPrefix = "fun"
        val levelSeparator = '$'
        val polishSignSymbol = '#' // added after polish signs are converted to allowed characters
        val charactersAllowedByNasm = listOf('a'..'z', 'A'..'Z', '0'..'9').flatten() +
            listOf('_', '$', '#', '@', '~', '.', '?')
        val knownConversionsToAllowedCharacters = mapOf(
            'ą' to 'a',
            'ć' to 'c',
            'ę' to 'e',
            'ł' to 'l',
            'ń' to 'n',
            'ó' to 'o',
            'ś' to 's',
            'ź' to 'x',
            'ż' to 'z',
            'Ą' to 'A',
            'Ć' to 'C',
            'Ę' to 'E',
            'Ł' to 'L',
            'Ń' to 'N',
            'Ó' to 'O',
            'Ś' to 'S',
            'Ź' to 'X',
            'Ż' to 'Z'
        )
        val forbiddenLabels = listOf(GlobalVariableAccessGenerator.GLOBALS_MEMORY_LABEL, DISPLAY_LABEL_IN_MEMORY)
    }

    private val knownIdentifiers: MutableSet<String> = mutableSetOf()
    fun build(prefix: String?, current: String): UniqueIdentifier {
        fun convertToPlainASCII(character: Char): List<Char> {
            if (character in charactersAllowedByNasm) return listOf(character)
            if (character in knownConversionsToAllowedCharacters) return listOf(
                knownConversionsToAllowedCharacters[character]!!,
                polishSignSymbol // to make mangled names more readable
            )
            throw IllegalCharacter(
                """
                Illegal character: $character.
                Characters allowed by NASM for labels are: ${charactersAllowedByNasm.joinToString()}.
            """
            )
        }
        val identifierBuilder = StringBuilder().append(prefix ?: functionPrefix).append(levelSeparator)
        current.forEach { convertToPlainASCII(it).forEach { character -> identifierBuilder.append(character) } }
        val identifier = identifierBuilder.toString()
        // at the time of the addition of this class to the codebase, this should not happen ($ and # are not allowed in function names)
        // this is a sanity check for if we ever relax the regex for function identifier and allow any of the special symbols used here
        if (!knownIdentifiers.add(identifier))
            throw InconsistentFunctionNamingConvention(
                """
            Two identical function labels were generated.
            This means that the function naming convention is not consistent across all levels of compilation.
            Are you allowing $levelSeparator or $polishSignSymbol in function names somewhere earlier on?
            """
            )

        // should not happen with current convention
        // sanity check in case we ever change the constants for this factory
        if (identifier in forbiddenLabels) throw InconsistentFunctionNamingConvention("Name $identifier is forbidden.")

        return UniqueIdentifier(identifier)
    }
}

data class UniqueIdentifier internal constructor(val value: String)
