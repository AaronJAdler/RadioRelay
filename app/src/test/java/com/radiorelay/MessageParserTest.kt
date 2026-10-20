package com.radiorelay

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageParserTest {

    private val stations = listOf(
        Station("98", "9169090985"),
        Station("96", "9169090969"),
        Station("94", "9169090947"),
    )

    private fun sends(text: String): List<Pair<String, String>> =
        MessageParser.parse(text, stations).map { it.station.number to it.keyword }

    @Test fun basicExamples() {
        assertEquals(listOf("9169090985" to "Minnie"), sends("98... Minnie"))
        assertEquals(listOf("9169090969" to "Chad"), sends("96... Chad"))
        assertEquals(listOf("9169090947" to "Dona"), sends("94... Dona"))
    }

    @Test fun multipleLinesInOneMessage() {
        assertEquals(
            listOf("9169090985" to "Minnie", "9169090969" to "Chad", "9169090947" to "Dona"),
            sends("98... Minnie\n96... Chad\n94... Dona"),
        )
    }

    @Test fun separatorVariants() {
        val expected = listOf("9169090985" to "Minnie")
        for (text in listOf(
            "98...Minnie", "98 ... Minnie", "98… Minnie", "98 - Minnie", "98: Minnie",
            "98.5... Minnie", "98.5 Minnie", "  98..  Minnie  ", "📻 98... Minnie", "98... Minnie!!",
            "98... \"Minnie\"", "98... Minnie 🎉", "98... Minnie (text by 5pm)",
        )) {
            assertEquals(text, expected, sends(text))
        }
    }

    @Test fun multiWordKeywordKept() {
        assertEquals(listOf("9169090985" to "Mickey Mouse"), sends("98... Mickey Mouse"))
    }

    @Test fun ordinaryChatterIgnored() {
        for (text in listOf(
            "98 Minnie", "Call 98... later", "I got 98... on my test lol", "98...", "98... !!!",
            "lol", "", "10:30 meet up", "99... Minnie",
        )) {
            assertEquals(text, emptyList<Pair<String, String>>(), sends(text))
        }
    }

    @Test fun decimalStationPrefix() {
        val s = listOf(Station("98.5", "9169090985"))
        assertEquals(1, MessageParser.parse("98.5... Minnie", s).size)
        assertEquals(0, MessageParser.parse("98.1... Minnie", s).size)
    }

    @Test fun phoneFormatting() {
        assertEquals("9169090985", PhoneUtil.normalize("(916) 909-0985"))
        assertEquals("(916) 909-0985", PhoneUtil.format("9169090985"))
        assertEquals("(916) 909-0985", PhoneUtil.format("19169090985"))
    }
}
