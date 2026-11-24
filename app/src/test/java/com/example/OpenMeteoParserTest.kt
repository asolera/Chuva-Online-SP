package com.example

import com.example.chuvaradar.data.OpenMeteoParser
import com.example.chuvaradar.data.RainTodayVerdict
import com.example.chuvaradar.data.WeatherVisualType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OpenMeteoParserTest {

    private fun buildSampleOpenMeteoJson(
        todayHourlyProbs: List<Int>,
        todayHourlyMm: List<Double> = List(24) { 0.0 }
    ): String {
        val hourlyTimes = (0..23).joinToString(",") { h ->
            "\"2026-11-23T${String.format("%02d", h)}:00\""
        }
        val probsStr = todayHourlyProbs.joinToString(",")
        val mmStr = todayHourlyMm.joinToString(",")
        val tempsStr = (0..23).joinToString(",") { h -> "${20 + (h % 8)}.0" }
        val codesStr = (0..23).joinToString(",") { h -> if (todayHourlyProbs[h] >= 40) "95" else "3" }

        return """
            {
              "latitude": -23.5614,
              "longitude": -46.6559,
              "current": {
                "temperature_2m": 26.4,
                "weather_code": 2
              },
              "hourly": {
                "time": [$hourlyTimes],
                "temperature_2m": [$tempsStr],
                "precipitation_probability": [$probsStr],
                "precipitation": [$mmStr],
                "weather_code": [$codesStr]
              },
              "daily": {
                "time": ["2026-11-23", "2026-11-24", "2026-11-25"],
                "weather_code": [95, 3, 61],
                "temperature_2m_max": [28.2, 31.0, 23.4],
                "temperature_2m_min": [22.1, 21.0, 17.2],
                "precipitation_probability_max": [75, 20, 60],
                "precipitation_sum": [8.4, 0.0, 4.2]
              }
            }
        """.trimIndent()
    }

    @Test
    fun `parses Open-Meteo current hourly and daily payload accurately`() {
        val probs = List(24) { h -> if (h == 15) 75 else 15 }
        val mm = List(24) { h -> if (h == 15) 4.2 else 0.0 }
        val json = buildSampleOpenMeteoJson(probs, mm)

        val data = OpenMeteoParser.parseForecastResponse(
            jsonText = json,
            locationLabel = "Av. Paulista",
            fetchedMillis = 1700000000000L
        )

        assertNotNull(data)
        assertEquals("Av. Paulista", data!!.locationLabel)
        assertEquals(26.4, data.currentTemperatureC!!, 0.01)
        assertEquals(WeatherVisualType.PARTLY_CLOUDY, data.currentCondition!!.visualType)
        assertEquals(3, data.days.size)

        val day0 = data.days[0]
        assertEquals("2026-11-23", day0.dateIso)
        assertEquals("seg.", day0.weekdayShort)
        assertEquals("23/11", day0.fullDateLabel)
        assertEquals(WeatherVisualType.THUNDERSTORM, day0.condition.visualType)
        assertEquals(24, day0.hourlyPoints.size)
        assertEquals(75, day0.hourlyPoints[15].precipitationProbability)
        assertEquals(4.2, day0.hourlyPoints[15].precipitationMm, 0.01)
    }

    @Test
    fun `evaluates Vai chover hoje as YES when high rain probability remains`() {
        val probs = List(24) { h -> if (h == 16) 75 else 10 }
        val mm = List(24) { h -> if (h == 16) 5.5 else 0.0 }
        val data = OpenMeteoParser.parseForecastResponse(
            buildSampleOpenMeteoJson(probs, mm),
            "Pinheiros",
            1700000000000L
        )!!

        val assessment = OpenMeteoParser.evaluateRainToday(
            forecast = data,
            currentHourSaoPaulo = 10,
            isLiveRadarRaining = false
        )

        assertEquals(RainTodayVerdict.YES, assessment.verdict)
        assertEquals("Sim", assessment.verdict.label)
        assertEquals(75, assessment.peakProbabilityPercent)
        assertEquals("16:00", assessment.peakHourLabel)
    }

    @Test
    fun `evaluates Vai chover hoje as MAYBE when moderate chance remains`() {
        val probs = List(24) { h -> if (h == 18) 40 else 10 }
        val mm = List(24) { h -> if (h == 18) 0.5 else 0.0 }
        val data = OpenMeteoParser.parseForecastResponse(
            buildSampleOpenMeteoJson(probs, mm),
            "Vila Mariana",
            1700000000000L
        )!!

        val assessment = OpenMeteoParser.evaluateRainToday(
            forecast = data,
            currentHourSaoPaulo = 9,
            isLiveRadarRaining = false
        )

        assertEquals(RainTodayVerdict.MAYBE, assessment.verdict)
        assertEquals("Talvez", assessment.verdict.label)
        assertEquals(40, assessment.peakProbabilityPercent)
    }

    @Test
    fun `evaluates Vai chover hoje as NO when remaining day is dry`() {
        val probs = List(24) { h -> if (h == 2) 80 else 10 }
        val mm = List(24) { h -> if (h == 2) 6.0 else 0.0 }
        val data = OpenMeteoParser.parseForecastResponse(
            buildSampleOpenMeteoJson(probs, mm),
            "Moema",
            1700000000000L
        )!!

        // Às 10h a chuva das 02h já passou; restante do dia tem no máximo 10%
        val assessment = OpenMeteoParser.evaluateRainToday(
            forecast = data,
            currentHourSaoPaulo = 10,
            isLiveRadarRaining = false
        )

        assertEquals(RainTodayVerdict.NO, assessment.verdict)
        assertEquals("Não", assessment.verdict.label)
        assertEquals(10, assessment.peakProbabilityPercent)
    }

    @Test
    fun `evaluates Vai chover hoje as YES when live radar confirms rain now`() {
        val probs = List(24) { 10 }
        val data = OpenMeteoParser.parseForecastResponse(
            buildSampleOpenMeteoJson(probs),
            "Centro",
            1700000000000L
        )!!

        val assessment = OpenMeteoParser.evaluateRainToday(
            forecast = data,
            currentHourSaoPaulo = 14,
            isLiveRadarRaining = true
        )

        assertEquals(RainTodayVerdict.YES, assessment.verdict)
        assertTrue(assessment.isLiveRadarRaining)
    }

    @Test
    fun `serializes and restores Open-Meteo cache envelope losslessly`() {
        val probs = List(24) { 25 }
        val raw = buildSampleOpenMeteoJson(probs)
        val wrapped = OpenMeteoParser.wrapCacheEnvelope(raw, "Santana", 1700000123456L)
        val restored = OpenMeteoParser.unwrapCacheEnvelope(wrapped)

        assertNotNull(restored)
        assertEquals("Santana", restored!!.locationLabel)
        assertEquals(1700000123456L, restored.fetchedMillis)
        assertEquals(3, restored.days.size)
    }
}
