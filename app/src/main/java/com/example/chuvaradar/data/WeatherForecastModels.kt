package com.example.chuvaradar.data

import androidx.compose.ui.graphics.Color

/**
 * Mapeamento de códigos meteorológicos WMO (World Meteorological Organization)
 * retornados pela API Open-Meteo para categorias visuais e descrições em PT-BR.
 */
enum class WeatherVisualType {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    THUNDERSTORM
}

enum class WmoWeatherCondition(
    val descriptionPt: String,
    val visualType: WeatherVisualType
) {
    CLEAR_SKY("Céu limpo", WeatherVisualType.CLEAR),
    MAINLY_CLEAR("Predomínio de sol", WeatherVisualType.PARTLY_CLOUDY),
    PARTLY_CLOUDY("Parcialmente nublado", WeatherVisualType.PARTLY_CLOUDY),
    OVERCAST("Nublado", WeatherVisualType.CLOUDY),
    FOG("Nevoeiro", WeatherVisualType.FOG),
    DRIZZLE("Garoa", WeatherVisualType.DRIZZLE),
    LIGHT_RAIN("Chuva fraca", WeatherVisualType.RAIN),
    MODERATE_RAIN("Chuva moderada", WeatherVisualType.RAIN),
    HEAVY_RAIN("Chuva forte", WeatherVisualType.HEAVY_RAIN),
    RAIN_SHOWERS("Pancadas de chuva", WeatherVisualType.RAIN),
    HEAVY_SHOWERS("Pancadas fortes", WeatherVisualType.HEAVY_RAIN),
    THUNDERSTORM("Tempestade", WeatherVisualType.THUNDERSTORM);

    companion object {
        fun fromWmoCode(code: Int): WmoWeatherCondition = when (code) {
            0 -> CLEAR_SKY
            1 -> MAINLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 66 -> LIGHT_RAIN
            63 -> MODERATE_RAIN
            65, 67 -> HEAVY_RAIN
            80, 81 -> RAIN_SHOWERS
            82 -> HEAVY_SHOWERS
            95, 96, 99 -> THUNDERSTORM
            else -> if (code >= 80) RAIN_SHOWERS else PARTLY_CLOUDY
        }
    }
}

/**
 * Veredito direto para o card "Vai chover hoje?".
 */
enum class RainTodayVerdict(
    val label: String,
    val accentColor: Color
) {
    YES("Sim", Color(0xFF38BDF8)),
    MAYBE("Talvez", Color(0xFFF59E0B)),
    NO("Não", Color(0xFF10B981))
}

/**
 * Avaliação consolidada da pergunta "Vai chover hoje?".
 */
data class RainTodayAssessment(
    val verdict: RainTodayVerdict,
    val headline: String,
    val detail: String,
    val peakProbabilityPercent: Int,
    val peakHourLabel: String?,
    val expectedRainMm: Double,
    val isLiveRadarRaining: Boolean = false
)

/**
 * Ponto horário (00h..23h) de chuva e temperatura retornado pelo Open-Meteo.
 */
data class HourlyRainPoint(
    val isoTime: String,
    val hourOfDay: Int,
    val hourLabel: String,
    val precipitationProbability: Int,
    val precipitationMm: Double,
    val temperatureC: Double,
    val weatherCode: Int
) {
    val condition: WmoWeatherCondition
        get() = WmoWeatherCondition.fromWmoCode(weatherCode)
}

/**
 * Previsão de um dia completo (resumo diário + lista das 24 horas).
 */
data class DailyForecastItem(
    val dateIso: String,
    val weekdayShort: String,
    val fullDateLabel: String,
    val weatherCode: Int,
    val maxTempC: Double,
    val minTempC: Double,
    val maxRainProbability: Int,
    val totalPrecipitationMm: Double,
    val hourlyPoints: List<HourlyRainPoint>
) {
    val condition: WmoWeatherCondition
        get() = WmoWeatherCondition.fromWmoCode(weatherCode)
}

/**
 * Pacote consolidado de previsão do tempo retornado pelo Open-Meteo.
 */
data class WeatherForecastData(
    val latitude: Double,
    val longitude: Double,
    val locationLabel: String,
    val currentTemperatureC: Double?,
    val currentWeatherCode: Int?,
    val days: List<DailyForecastItem>,
    val fetchedMillis: Long
) {
    val currentCondition: WmoWeatherCondition?
        get() = currentWeatherCode?.let { WmoWeatherCondition.fromWmoCode(it) }
}

/**
 * Estado de UI observado pelos cards "Vai chover hoje?" e "Previsão do Tempo".
 */
data class WeatherForecastUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isFromCache: Boolean = false,
    val forecast: WeatherForecastData? = null,
    val todayAssessment: RainTodayAssessment? = null,
    val errorMessage: String? = null,
    val sourceUrl: String = "https://open-meteo.com/"
)
