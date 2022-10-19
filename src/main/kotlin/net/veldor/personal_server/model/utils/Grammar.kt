package net.veldor.personal_server.model.utils

import java.text.CharacterIterator
import java.text.StringCharacterIterator
import java.util.regex.Pattern


object Grammar {
    fun getRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    fun humanReadableByteCountBin(bytes: Long): String {
        val absB = if (bytes == Long.MIN_VALUE) Long.MAX_VALUE else Math.abs(bytes)
        if (absB < 1024) {
            return "$bytes б"
        }
        var value = absB
        val ci: CharacterIterator = StringCharacterIterator("КМГТПЭ")
        var i = 40
        while (i >= 0 && absB > 0xfffccccccccccccL shr i) {
            value = value shr 10
            ci.next()
            i -= 10
        }
        value *= java.lang.Long.signum(bytes).toLong()
        return String.format("%.1f %cб", value / 1024.0, ci.current())
    }

    fun clearUnreadableChars(content: String): String {
        val re = Regex("[^A-Za-z0-9 ]")
        return re.replace(content, "")
    }

    fun getDicomExecutionId(content: String): String? {
        val regex = "LO([ TA\\d]+)0DA".toRegex()
        val matches = regex.find(content)
        if (matches != null) {
            return matches.groupValues[1].trim()
        }
        return null
    }
}