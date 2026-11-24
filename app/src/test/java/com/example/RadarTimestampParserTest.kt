package com.example

import com.example.chuvaradar.data.RadarTimestampParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RadarTimestampParserTest {

    @Test
    fun testParseDateStringUtc() {
        // "2026-11-07 as 20:30 UTC"
        val millis = RadarTimestampParser.parseDateString("2026-11-07 as 20:30 UTC")
        assertNotNull(millis)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = millis!!
        }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
        assertEquals(7, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testParseHtmlLegendaHl() {
        // "2026-11-07 as 17:36 HL" (Hora Local de SP = UTC-3)
        val millis = RadarTimestampParser.parseHtmlLegenda("2026-11-07 as 17:36 HL")
        assertNotNull(millis)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo")).apply {
            timeInMillis = millis!!
        }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
        assertEquals(7, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(36, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testAgeFormatting() {
        assertEquals("há 15 min", RadarTimestampParser.formatDataAgeDescription(15))
        assertEquals("há ~1 hora", RadarTimestampParser.formatDataAgeDescription(90))
        assertEquals("há 3 horas", RadarTimestampParser.formatDataAgeDescription(180))
        assertEquals("há 1 dia", RadarTimestampParser.formatDataAgeDescription(1440))
        assertEquals("há 2 dias", RadarTimestampParser.formatDataAgeDescription(2880))
    }
}
