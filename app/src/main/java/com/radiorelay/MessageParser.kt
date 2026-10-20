package com.radiorelay

/** A radio station's text line: messages starting with [prefix] (e.g. "98") get texted to [number]. */
data class Station(val prefix: String, val number: String)

/** One text to send: [keyword] goes to [station]'s number. */
data class SendCommand(val station: Station, val keyword: String)

/**
 * Turns group-chat text like "98... Minnie" into "text Minnie to the 98 line".
 *
 * Accepted forms (one entry per line, several lines per message are fine):
 *   98... Minnie     98…Minnie     98 - Minnie     98: Minnie     98.5... Minnie     98.5 Minnie
 *
 * Not accepted (to avoid texting the station from ordinary chatter):
 *   "98 Minnie" (no separator and no decimal), "Call 98... later" (doesn't start with the number).
 */
object MessageParser {

    // leading bullets/emoji, station number, optional decimal, optional separator, keyword
    private val ENTRY = Regex(
        """^[^\p{L}\p{N}]*(\d{2,3})(?:[.,](\d{1,2}))?\s*(\.{2,}|…+|[-–—:=>|]+)?\s*(.+)$"""
    )
    private val VALID_PREFIX = Regex("""\d{2,3}(\.\d{1,2})?""")
    private val BRACKETED = Regex("""\([^)]*\)|\[[^\]]*]""")
    private val NOT_KEYWORD_CHAR = Regex("""[^\p{L}\p{N}\s'&-]""")
    private val SPACES = Regex("""\s+""")

    fun isValidPrefix(prefix: String): Boolean = VALID_PREFIX.matches(prefix.trim())

    fun parse(text: String, stations: List<Station>): List<SendCommand> {
        val out = mutableListOf<SendCommand>()
        for (rawLine in text.split('\n', '\r', ' ')) {
            val m = ENTRY.matchEntire(rawLine.trim()) ?: continue
            val whole = m.groupValues[1]
            val decimal = m.groups[2]?.value
            val separator = m.groups[3]?.value
            if (separator == null && decimal == null) continue

            val keyword = cleanKeyword(m.groupValues[4])
            if (keyword.isEmpty()) continue

            val station = stations.firstOrNull { prefixMatches(it.prefix, whole, decimal) } ?: continue
            out += SendCommand(station, keyword)
        }
        return out
    }

    /** "Minnie!! 🎉" -> "Minnie", "Chad (by 5pm)" -> "Chad". */
    fun cleanKeyword(raw: String): String =
        raw.replace(BRACKETED, " ")
            .replace(NOT_KEYWORD_CHAR, " ")
            .replace(SPACES, " ")
            .trim()
            .trim('-', '\'', '&')
            .trim()
            .take(60)
            .trim()

    private fun prefixMatches(prefix: String, whole: String, decimal: String?): Boolean {
        val p = prefix.trim().replace(',', '.')
        if (p == whole) return true
        return decimal != null && p == "$whole.$decimal"
    }
}
