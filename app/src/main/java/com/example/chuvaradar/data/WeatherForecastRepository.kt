package com.example.chuvaradar.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Repositório responsável por consultar a previsão horária e diária (8 dias)
 * via API gratuita Open-Meteo (https://open-meteo.com/) usando as coordenadas
 * do endereço ativo do usuário, com cache em disco para abertura instantânea.
 */
class WeatherForecastRepository(private val context: Context) {

    companion object {
        private const val TAG = "WeatherForecastRepo"
        private const val OPEN_METEO_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
        private const val FORECAST_DAYS = 8
        private const val DEFAULT_SP_LAT = -23.5614
        private const val DEFAULT_SP_LON = -46.6559
        private const val DEFAULT_SP_LABEL = "São Paulo - SP"

        @Volatile
        private var instance: WeatherForecastRepository? = null

        fun getInstance(context: Context): WeatherForecastRepository {
            return instance ?: synchronized(this) {
                instance ?: WeatherForecastRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cacheRoot = File(context.cacheDir, "open_meteo_forecast_v1").apply { mkdirs() }
    private val snapshotFile = File(cacheRoot, "latest_forecast.json")

    private val _uiState = MutableStateFlow(WeatherForecastUiState(isLoading = true))
    val uiState: StateFlow<WeatherForecastUiState> = _uiState.asStateFlow()

    @Volatile
    private var lastLiveRadarRaining: Boolean = false

    init {
        loadSnapshotFromDiskIfAvailable()
    }

    private fun loadSnapshotFromDiskIfAvailable() {
        try {
            if (!snapshotFile.exists()) return
            val cached = OpenMeteoParser.unwrapCacheEnvelope(snapshotFile.readText()) ?: return
            val assessment = OpenMeteoParser.evaluateRainToday(
                forecast = cached,
                isLiveRadarRaining = lastLiveRadarRaining
            )
            _uiState.value = WeatherForecastUiState(
                isLoading = false,
                isRefreshing = false,
                isFromCache = true,
                forecast = cached,
                todayAssessment = assessment,
                errorMessage = null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao carregar cache do Open-Meteo: ${e.message}")
        }
    }

    /**
     * Atualiza o veredito de hoje caso o radar ao vivo informe chuva ativa neste momento.
     */
    fun updateLiveRadarRainStatus(isRainingNow: Boolean) {
        if (lastLiveRadarRaining == isRainingNow) return
        lastLiveRadarRaining = isRainingNow
        val currentForecast = _uiState.value.forecast ?: return
        val recalculated = OpenMeteoParser.evaluateRainToday(
            forecast = currentForecast,
            isLiveRadarRaining = isRainingNow
        )
        _uiState.value = _uiState.value.copy(todayAssessment = recalculated)
    }

    suspend fun refresh(
        latitude: Double = DEFAULT_SP_LAT,
        longitude: Double = DEFAULT_SP_LON,
        locationLabel: String = DEFAULT_SP_LABEL,
        isLiveRadarRaining: Boolean = lastLiveRadarRaining,
        showLoadingIndicator: Boolean = false
    ) = withContext(Dispatchers.IO) {
        lastLiveRadarRaining = isLiveRadarRaining
        val hadData = _uiState.value.forecast != null
        _uiState.value = _uiState.value.copy(
            isLoading = showLoadingIndicator && !hadData,
            isRefreshing = true,
            errorMessage = null
        )

        try {
            val url = buildForecastUrl(latitude, longitude)
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "ChuvaRadarSP/1.0 (Android)")
                .build()

            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} ao consultar Open-Meteo.")
                }
                response.body?.string()
                    ?: throw IllegalStateException("Resposta vazia do servidor Open-Meteo.")
            }

            val nowMillis = System.currentTimeMillis()
            val parsed = OpenMeteoParser.parseForecastResponse(
                jsonText = body,
                locationLabel = locationLabel,
                fetchedMillis = nowMillis
            ) ?: throw IllegalStateException("Não foi possível interpretar a previsão do Open-Meteo.")

            saveSnapshotToDisk(body, locationLabel, nowMillis)

            val assessment = OpenMeteoParser.evaluateRainToday(
                forecast = parsed,
                isLiveRadarRaining = lastLiveRadarRaining
            )

            _uiState.value = WeatherForecastUiState(
                isLoading = false,
                isRefreshing = false,
                isFromCache = false,
                forecast = parsed,
                todayAssessment = assessment,
                errorMessage = null
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Erro ao consultar Open-Meteo: ${e.message}", e)

            val current = _uiState.value
            if (current.forecast != null) {
                _uiState.value = current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isFromCache = true,
                    errorMessage = "Sem conexão com o Open-Meteo no momento. Exibindo última previsão salva."
                )
            } else {
                _uiState.value = current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = "Não foi possível carregar a previsão do tempo (Open-Meteo). Verifique a conexão e tente novamente."
                )
            }
        }
    }

    private fun buildForecastUrl(latitude: Double, longitude: Double): String {
        val latStr = String.format(Locale.US, "%.4f", latitude)
        val lonStr = String.format(Locale.US, "%.4f", longitude)
        return "$OPEN_METEO_ENDPOINT" +
            "?latitude=$latStr" +
            "&longitude=$lonStr" +
            "&current=temperature_2m,weather_code" +
            "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum" +
            "&timezone=America%2FSao_Paulo" +
            "&forecast_days=$FORECAST_DAYS"
    }

    private fun saveSnapshotToDisk(rawJson: String, locationLabel: String, fetchedMillis: Long) {
        try {
            snapshotFile.writeText(
                OpenMeteoParser.wrapCacheEnvelope(rawJson, locationLabel, fetchedMillis)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao salvar cache do Open-Meteo: ${e.message}")
        }
    }
}
