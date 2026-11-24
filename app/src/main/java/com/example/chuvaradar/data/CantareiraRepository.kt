package com.example.chuvaradar.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Repositório responsável por consultar os dados oficiais do Sistema Cantareira (local 64)
 * diretamente no portal Mananciais da SABESP (https://mananciais.sabesp.com.br),
 * cobrindo passo temporal diário no período de 1 mês (30 dias).
 */
class CantareiraRepository(private val context: Context) {

    companion object {
        private const val TAG = "CantareiraRepository"
        private const val HISTORY_DAYS = 30
        private const val MAX_CONCURRENT_REQUESTS = 6

        private const val V4_DAILY_SUMMARY_BASE =
            "https://mananciais.sabesp.com.br/api/v4/sistemas/dados/resumo-diario/"
        private const val LEGACY_DAILY_SUMMARY_BASE =
            "https://mananciais.sabesp.com.br/api/Mananciais/ResumoSistemas/"

        @Volatile
        private var instance: CantareiraRepository? = null

        fun getInstance(context: Context): CantareiraRepository {
            return instance ?: synchronized(this) {
                instance ?: CantareiraRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private enum class EndpointMode {
        V4_RESUMO_DIARIO,
        LEGACY_RESUMO_SISTEMAS
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cacheRoot = File(context.cacheDir, "sabesp_cantareira_v1").apply { mkdirs() }
    private val daysCacheDir = File(cacheRoot, "days").apply { mkdirs() }
    private val snapshotFile = File(cacheRoot, "latest_snapshot.json")

    private val _uiState = MutableStateFlow(CantareiraUiState(isLoading = true))
    val uiState: StateFlow<CantareiraUiState> = _uiState.asStateFlow()

    init {
        loadSnapshotFromDiskIfAvailable()
    }

    private fun loadSnapshotFromDiskIfAvailable() {
        try {
            if (!snapshotFile.exists()) return
            val parsed = SabespCantareiraParser.deserializeSnapshot(snapshotFile.readText()) ?: return
            val (fetchedMillis, cachedPoints) = parsed
            if (cachedPoints.isNotEmpty()) {
                _uiState.value = buildSuccessState(
                    rawPoints = cachedPoints,
                    isFromCache = true,
                    fetchedMillis = fetchedMillis
                ).copy(isLoading = false, isRefreshing = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao carregar cache do Cantareira: ${e.message}")
        }
    }

    suspend fun refresh(showLoadingIndicator: Boolean = false) = withContext(Dispatchers.IO) {
        val hadData = _uiState.value.points.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            isLoading = showLoadingIndicator && !hadData,
            isRefreshing = true,
            errorMessage = null
        )

        try {
            val targetDates = buildRecentSaoPauloDates(HISTORY_DAYS)
            val todayIso = targetDates.last()
            val yesterdayIso = targetDates.getOrNull(targetDates.size - 2) ?: todayIso

            // 1. Descobre qual endpoint oficial da SABESP está respondendo para os dados mais recentes
            val probeDates = targetDates.takeLast(3).reversed()
            val detectedMode = detectWorkingEndpointMode(probeDates)
                ?: throw IllegalStateException("Não foi possível obter resposta do portal Mananciais da SABESP.")

            // 2. Busca os últimos 30 dias em paralelo com limite de concorrência e cache em disco para dias históricos
            val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
            val fetchedPoints = coroutineScope {
                targetDates.map { dateIso ->
                    async(Dispatchers.IO) {
                        val isRecentMutableDay = (dateIso == todayIso || dateIso == yesterdayIso)
                        if (!isRecentMutableDay) {
                            readCachedDay(dateIso)?.let { return@async it }
                        }

                        semaphore.withPermit {
                            val livePoint = fetchDayWithFallback(dateIso, detectedMode)
                            if (livePoint != null) {
                                writeCachedDay(livePoint)
                                livePoint
                            } else {
                                readCachedDay(dateIso)
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            if (fetchedPoints.isEmpty()) {
                throw IllegalStateException("Nenhum dado recente do Sistema Cantareira foi retornado pela SABESP.")
            }

            val enriched = enrichDailyVariations(fetchedPoints)
            val nowMillis = System.currentTimeMillis()
            saveSnapshotToDisk(enriched, nowMillis)

            _uiState.value = buildSuccessState(
                rawPoints = enriched,
                isFromCache = false,
                fetchedMillis = nowMillis
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Erro ao consultar Sistema Cantareira (SABESP): ${e.message}", e)

            val current = _uiState.value
            if (current.points.isNotEmpty()) {
                _uiState.value = current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isFromCache = true,
                    errorMessage = "Sem conexão com a SABESP no momento. Exibindo últimos dados oficiais salvos."
                )
            } else {
                _uiState.value = current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = "Não foi possível carregar os dados oficiais do Sistema Cantareira (SABESP). Verifique a conexão e tente novamente."
                )
            }
        }
    }

    private fun detectWorkingEndpointMode(probeDates: List<String>): EndpointMode? {
        for (dateIso in probeDates) {
            if (fetchDayFromEndpoint(dateIso, EndpointMode.V4_RESUMO_DIARIO) != null) {
                return EndpointMode.V4_RESUMO_DIARIO
            }
        }
        for (dateIso in probeDates) {
            if (fetchDayFromEndpoint(dateIso, EndpointMode.LEGACY_RESUMO_SISTEMAS) != null) {
                return EndpointMode.LEGACY_RESUMO_SISTEMAS
            }
        }
        return null
    }

    private fun fetchDayWithFallback(dateIso: String, primaryMode: EndpointMode): CantareiraDailyPoint? {
        val primaryResult = fetchDayFromEndpoint(dateIso, primaryMode)
        if (primaryResult != null) return primaryResult

        val fallbackMode = if (primaryMode == EndpointMode.V4_RESUMO_DIARIO) {
            EndpointMode.LEGACY_RESUMO_SISTEMAS
        } else {
            EndpointMode.V4_RESUMO_DIARIO
        }
        return fetchDayFromEndpoint(dateIso, fallbackMode)
    }

    private fun fetchDayFromEndpoint(dateIso: String, mode: EndpointMode): CantareiraDailyPoint? {
        val url = when (mode) {
            EndpointMode.V4_RESUMO_DIARIO -> "$V4_DAILY_SUMMARY_BASE$dateIso"
            EndpointMode.LEGACY_RESUMO_SISTEMAS -> "$LEGACY_DAILY_SUMMARY_BASE$dateIso"
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")
                .header("Referer", "https://mananciais.sabesp.com.br/")
                .header("Origin", "https://mananciais.sabesp.com.br")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                SabespCantareiraParser.parseDailyResponse(body, dateIso)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha em $url: ${e.message}")
            null
        }
    }

    private fun buildRecentSaoPauloDates(days: Int): List<String> {
        val tz = TimeZone.getTimeZone("America/Sao_Paulo")
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = tz
        }
        val cal = Calendar.getInstance(tz)
        val dates = ArrayList<String>(days)
        for (offset in (days - 1) downTo 0) {
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -offset)
            dates.add(formatter.format(c.time))
        }
        return dates
    }

    private fun enrichDailyVariations(rawPoints: List<CantareiraDailyPoint>): List<CantareiraDailyPoint> {
        val sorted = rawPoints.distinctBy { it.dateIso }.sortedBy { it.dateIso }
        return sorted.mapIndexed { index, point ->
            val variation = point.dailyVariationPercent ?: if (index > 0) {
                point.volumePercent - sorted[index - 1].volumePercent
            } else {
                null
            }
            point.copy(dailyVariationPercent = variation)
        }
    }

    private fun buildSuccessState(
        rawPoints: List<CantareiraDailyPoint>,
        isFromCache: Boolean,
        fetchedMillis: Long
    ): CantareiraUiState {
        val sorted = enrichDailyVariations(rawPoints)
        val latest = sorted.lastOrNull()
        val first = sorted.firstOrNull()
        val delta30d = if (latest != null && first != null && sorted.size >= 2) {
            latest.volumePercent - first.volumePercent
        } else null
        val minVol = sorted.minOfOrNull { it.volumePercent }
        val maxVol = sorted.maxOfOrNull { it.volumePercent }

        return CantareiraUiState(
            isLoading = false,
            isRefreshing = false,
            isFromCache = isFromCache,
            points = sorted,
            latest = latest,
            delta30DaysPercent = delta30d,
            minVolume30Days = minVol,
            maxVolume30Days = maxVol,
            lastFetchedMillis = fetchedMillis,
            errorMessage = null
        )
    }

    private fun readCachedDay(dateIso: String): CantareiraDailyPoint? {
        return try {
            val file = File(daysCacheDir, "$dateIso.json")
            if (!file.exists()) return null
            SabespCantareiraParser.deserializePoint(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCachedDay(point: CantareiraDailyPoint) {
        try {
            val file = File(daysCacheDir, "${point.dateIso}.json")
            file.writeText(SabespCantareiraParser.serializePoint(point))
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao salvar dia em cache (${point.dateIso}): ${e.message}")
        }
    }

    private fun saveSnapshotToDisk(points: List<CantareiraDailyPoint>, fetchedMillis: Long) {
        try {
            snapshotFile.writeText(SabespCantareiraParser.serializeSnapshot(points, fetchedMillis))
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao salvar snapshot do Cantareira: ${e.message}")
        }
    }
}
