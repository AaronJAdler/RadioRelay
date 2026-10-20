package com.radiorelay

object PhoneUtil {
    /** "(916) 909-0985" -> "9169090985"; keeps a leading + for international numbers. */
    fun normalize(raw: String): String {
        val t = raw.trim()
        val digits = t.filter { it.isDigit() }
        return if (t.startsWith("+") && digits.isNotEmpty()) "+$digits" else digits
    }

    fun isValid(number: String): Boolean = number.count { it.isDigit() } in 10..15

    /** "9169090985" -> "(916) 909-0985". Anything else is returned unchanged. */
    fun format(number: String): String {
        val d = number.filter { it.isDigit() }
        val core = if (d.length == 11 && d.startsWith("1")) d.substring(1) else d
        return if (core.length == 10) {
            "(${core.substring(0, 3)}) ${core.substring(3, 6)}-${core.substring(6)}"
        } else {
            number
        }
    }
}
