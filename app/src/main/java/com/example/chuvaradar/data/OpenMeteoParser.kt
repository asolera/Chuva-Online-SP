package com.example.chuvaradar.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Parser da API Open-Meteo e avaliador do veredito "Vai chover hoje?" (Sim / Talvez / Não).
 */
object OpenMeteoParser {

    private val SAO_PAULO_TZ: TimeZone = TimeZone.getTimeZone("America/Sao_Paulo")

    private val WEEKDAY_SHORT_PT = mapOf(
        Calendar.SUNDAY to "dom.",
        Calendar.MONDAY to "seg.",
        Calendar.TUESDAY to "ter.",
        Calendar.WEDNESDAY to "qua.",
        Calendar.THURSDAY to "qui.",
        Calendar.FRIDAY to "sex.",
        Calendar.SATURDAY to "sáb."
    )

    /**
     * Faz parse da resposta JSON completa do endpoint /v1/forecast do Open-Meteo.
     */
    fun parseForecastResponse(
        jsonText: String,
        locationLabel: String,
        fetchedMillis: Long
    ): WeatherForecastData? {
        val trimmed = jsonText.trim()
        if (!trimmed.startsWith("{")) return null

        return try {
            val root = JSONObject(trimmed)
            val lat = root.optDouble("latitude", Double.NaN)
            val lon = root.optDouble("longitude", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return null

            val currentObj = root.optJSONObject("current")
            val currentTemp = if (currentObj != null && currentObj.has("temperature_2m") && !currentObj.isNull("temperature_2m")) {
                currentObj.optDouble("temperature_2m", Double.NaN).takeIf { !it.isNaN() }
            } else null
            val currentCode = if (currentObj != null && currentObj.has("weather_code") && !currentObj.isNull("weather_code")) {
                currentObj.optInt("weather_code", -1).takeIf { it >= 0 }
            } else null

            // Agrupa pontos horários por data YYYY-MM-DD
            val hourlyByDate = mutableMapOf<String, MutableList<HourlyRainPoint>>()
            val hourlyObj = root.optJSONObject("hourly")
            if (hourlyObj != null) {
                val times = hourlyObj.optJSONArray("time")
                val probs = hourlyObj.optJSONArray("precipitation_probability")
                val precips = hourlyObj.optJSONArray("precipitation")
                val temps = hourlyObj.optJSONArray("temperature_2m")
                val codes = hourlyObj.optJSONArray("weather_code")

                val count = times?.length() ?: 0
                for (i in 0 until count) {
                    val isoTime = times?.optString(i, "") ?: ""
                    if (isoTime.length < 13) continue
                    val datePart = isoTime.substring(0, 10)
                    val hourInt = isoTime.substring(11, 13).toIntOrNull() ?: continue
                    val hourLabel = String.format(Locale.US, "%02d:00", hourInt)

                    val prob = if (probs != null && i < probs.length() && !probs.isNull(i)) {
                        probs.optInt(i, 0).coerceIn(0, 100)
                    } else 0
                    val mm = if (precips != null && i < precips.length() && !precips.isNull(i)) {
                        precips.optDouble(i, 0.0).takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0
                    } else 0.0
                    val temp = if (temps != null && i < temps.length() && !temps.isNull(i)) {
                        temps.optDouble(i, 22.0).takeIf { !it.isNaN() } ?: 22.0
                    } else 22.0
                    val code = if (codes != null && i < codes.length() && !codes.isNull(i)) {
                        codes.optInt(i, 0)
                    } else 0

                    hourlyByDate.getOrPut(datePart) { mutableListOf() }.add(
                        HourlyRainPoint(
                            isoTime = isoTime,
                            hourOfDay = hourInt,
                            hourLabel = hourLabel,
                            precipitationProbability = prob,
                            precipitationMm = mm,
                            temperatureC = temp,
                            weatherCode = code
                        )
                    )
                }
            }

            // Lê previsão diária (até 8 dias)
            val dailyObj = root.optJSONObject("daily") ?: return null
            val dailyTimes = dailyObj.optJSONArray("time") ?: return null
            val dailyCodes = dailyObj.optJSONArray("weather_code")
            val dailyMaxTemps = dailyObj.optJSONArray("temperature_2m_max")
            val dailyMinTemps = dailyObj.optJSONArray("temperature_2m_min")
            val dailyMaxProbs = dailyObj.optJSONArray("precipitation_probability_max")
            val dailyPrecipSum = dailyObj.optJSONArray("precipitation_sum")

            val days = mutableListOf<DailyForecastItem>()
            for (i in 0 until dailyTimes.length()) {
                val dateIso = dailyTimes.optString(i, "")
                if (dateIso.length < 10) continue

                val hourlyForDay = hourlyByDate[dateIso]?.sortedBy { it.hourOfDay } ?: emptyList()
                val weatherCode = if (dailyCodes != null && i < dailyCodes.length() && !dailyCodes.isNull(i)) {
                    dailyCodes.optInt(i, 0)
                } else {
                    hourlyForDay.maxByOrNull { it.precipitationProbability }?.weatherCode ?: 0
                }
                val maxTemp = if (dailyMaxTemps != null && i < dailyMaxTemps.length() && !dailyMaxTemps.isNull(i)) {
                    dailyMaxTemps.optDouble(i, 25.0)
                } else {
                    hourlyForDay.maxOfOrNull { it.temperatureC } ?: 25.0
                }
                val minTemp = if (dailyMinTemps != null && i < dailyMinTemps.length() && !dailyMinTemps.isNull(i)) {
                    dailyMinTemps.optDouble(i, 18.0)
                } else {
                    hourlyForDay.minOfOrNull { it.temperatureC } ?: 18.0
                }
                val maxProb = if (dailyMaxProbs != null && i < dailyMaxProbs.length() && !dailyMaxProbs.isNull(i)) {
                    dailyMaxProbs.optInt(i, 0).coerceIn(0, 100)
                } else {
                    hourlyForDay.maxOfOrNull { it.precipitationProbability } ?: 0
                }
                val totalMm = if (dailyPrecipSum != null && i < dailyPrecipSum.length() && !dailyPrecipSum.isNull(i)) {
                    dailyPrecipSum.optDouble(i, 0.0).coerceAtLeast(0.0)
                } else {
                    hourlyForDay.sumOf { it.precipitationMm }
                }

                days.add(
                    DailyForecastItem(
                        dateIso = dateIso,
                        weekdayShort = weekdayShortPt(dateIso),
                        fullDateLabel = formatFullDatePt(dateIso),
                        weatherCode = weatherCode,
                        maxTempC = maxTemp,
                        minTempC = minTemp,
                        maxRainProbability = maxProb,
                        totalPrecipitationMm = totalMm,
                        hourlyPoints = hourlyForDay
                    )
                )
            }

            if (days.isEmpty()) return null

            WeatherForecastData(
                latitude = lat,
                longitude = lon,
                locationLabel = locationLabel,
                currentTemperatureC = currentTemp,
                currentWeatherCode = currentCode,
                days = days,
                fetchedMillis = fetchedMillis
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Avalia a resposta para "Vai chover hoje?" (Sim, Talvez ou Não)
     * com base nas horas restantes do dia atual (fuso America/Sao_Paulo) e,
     * opcionalmente, confirmando com o radar ao vivo quando está chovendo agora.
     */
    fun evaluateRainToday(
        forecast: WeatherForecastData,
        currentHourSaoPaulo: Int = currentSaoPauloHour(),
        isLiveRadarRaining: Boolean = false
    ): RainTodayAssessment {
        val today = forecast.days.firstOrNull()
            ?: return RainTodayAssessment(
                verdict = RainTodayVerdict.NO,
                headline = "Sem previsão de chuva",
                detail = "Dados horários indisponíveis no momento.",
                peakProbabilityPercent = 0,
                peakHourLabel = null,
                expectedRainMm = 0.0
            )

        val remainingHours = today.hourlyPoints.filter { it.hourOfDay >= currentHourSaoPaulo }
            .ifEmpty { today.hourlyPoints }

        val peakPoint = remainingHours.maxByOrNull { it.precipitationProbability }
        val peakProb = peakPoint?.precipitationProbability ?: today.maxRainProbability
        val remainingMm = if (remainingHours.isNotEmpty()) {
            remainingHours.sumOf { it.precipitationMm }
        } else {
            today.totalPrecipitationMm
        }
        val peakHourLabel = peakPoint?.takeIf { it.precipitationProbability > 0 }?.hourLabel

        // Janela provável (primeira hora restante >= 40% ou hora de pico)
        val firstLikelyHour = remainingHours.firstOrNull { it.precipitationProbability >= 45 }?.hourLabel
            ?: peakHourLabel

        val verdict = when {
            isLiveRadarRaining -> RainTodayVerdict.YES
            peakProb >= 60 -> RainTodayVerdict.YES
            peakProb >= 50 && remainingMm >= 2.0 -> RainTodayVerdict.YES
            remainingMm >= 3.0 && peakProb >= 35 -> RainTodayVerdict.YES
            peakProb in 25..59 -> RainTodayVerdict.MAYBE
            remainingMm >= 0.4 -> RainTodayVerdict.MAYBE
            else -> RainTodayVerdict.NO
        }

        val mmFormatted = String.format(Locale("pt", "BR"), "%.1f", remainingMm)

        val (headline, detail) = when (verdict) {
            RainTodayVerdict.YES -> {
                if (isLiveRadarRaining) {
                    "Chuva ativa detectada no radar" to buildString {
                        append("Precipitação na sua região agora")
                        if (peakProb > 0) {
                            append(" • pico de $peakProb%")
                            if (peakHourLabel != null) append(" às $peakHourLabel")
                        }
                    }
                } else {
                    val timeHint = if (firstLikelyHour != null) "por volta das $firstLikelyHour" else "ao longo do dia"
                    "Alta chance de chuva $timeHint" to buildString {
                        append("Pico de $peakProb%")
                        if (peakHourLabel != null) append(" às $peakHourLabel")
                        if (remainingMm >= 0.2) append(" • ~$mmFormatted mm previstos")
                    }
                }
            }
            RainTodayVerdict.MAYBE -> {
                val timeHint = if (peakHourLabel != null) "perto das $peakHourLabel" else "em alguns horários"
                "Possibilidade de chuva isolada" to buildString {
                    append("Chance de até $peakProb% $timeHint")
                    if (remainingMm >= 0.2) append(" • ~$mmFormatted mm")
                    else append(" • leve um guarda-chuva")
                }
            }
            RainTodayVerdict.NO -> {
                val maxLabel = if (peakProb > 0) "máx. $peakProb%" else "0%"
                "Tempo firme no restante do dia" to "Probabilidade baixa hoje ($maxLabel) e sem acumulado relevante."
            }
        }

        return RainTodayAssessment(
            verdict = verdict,
            headline = headline,
            detail = detail,
            peakProbabilityPercent = peakProb,
            peakHourLabel = peakHourLabel,
            expectedRainMm = (remainingMm * 10.0).roundToInt() / 10.0,
            isLiveRadarRaining = isLiveRadarRaining
        )
    }

    /**
     * Retorna o rótulo curto do dia da semana em PT-BR (ex.: "seg.", "ter.").
     */
    fun weekdayShortPt(dateIso: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = SAO_PAULO_TZ
            }
            val date = sdf.parse(dateIso) ?: return dateIso
            val cal = Calendar.getInstance(SAO_PAULO_TZ).apply { time = date }
            WEEKDAY_SHORT_PT[cal.get(Calendar.DAY_OF_WEEK)] ?: dateIso
        } catch (_: Exception) {
            dateIso
        }
    }

    fun formatFullDatePt(dateIso: String): String {
        val parts = dateIso.split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}" else dateIso
    }

    fun currentSaoPauloHour(): Int {
        val cal = Calendar.getInstance(SAO_PAULO_TZ)
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * Envelopa o JSON cru da Open-Meteo junto com metadados para cache em disco.
     */
    fun wrapCacheEnvelope(
        rawOpenMeteoJson: String,
        locationLabel: String,
        fetchedMillis: Long
    ): String {
        val envelope = JSONObject()
        envelope.put("locationLabel", locationLabel)
        envelope.put("fetchedMillis", fetchedMillis)
        envelope.put("payload", JSONObject(rawOpenMeteoJson))
        return envelope.toString()
    }

    fun unwrapCacheEnvelope(envelopeJson: String): WeatherForecastData? {
        return try {
            val envelope = JSONObject(envelopeJson)
            val label = envelope.optString("locationLabel", "São Paulo - SP")
            val fetchedMillis = envelope.optLong("fetchedMillis", 0L)
            val payload = envelope.optJSONObject("payload") ?: return null
            parseForecastResponse(payload.toString(), label, fetchedMillis)
        } catch (_: Exception) {
            null
        }
    }
}
