package com.example.chuvaradar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.chuvaradar.engine.RadarAnalysisEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class RadarUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val frames: List<RadarScanFrame> = emptyList(),
    val activeFrameIndex: Int = 0,
    val nowcast: NowcastEstimate? = null,
    val cityCoverage: CityRainCoverage? = null,
    val districtStatuses: List<DistrictRainStatus> = emptyList(),
    val activeAddress: UserSavedAddress? = null,
    val lastUpdateTime: Long = 0L,
    val errorMessage: String? = null,
    val isDataOutdated: Boolean = false,
    val dataAgeMinutes: Long = 0L,
    val dataAgeDescription: String = "",
    val outdatedWarningMessage: String? = null
)

class RadarRepository(
    private val context: Context,
    private val preferencesDataStore: UserPreferencesDataStore
) {
    companion object {
        private const val TAG = "RadarRepository"
        private const val CHUVA_ONLINE_URL = "https://www.chuvaonline.iag.usp.br/animacao_36km.php"
        private const val BASE_IMAGE_LOOP_URL = "https://www.starnet.iag.usp.br/img_starnet/Radar_USP/pelletron_36km/loop/36km_loop_"

        @Volatile
        private var instance: RadarRepository? = null

        fun getInstance(context: Context): RadarRepository {
            return instance ?: synchronized(this) {
                instance ?: RadarRepository(
                    context.applicationContext,
                    UserPreferencesDataStore(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow(RadarUiState(isLoading = true))
    val uiState: StateFlow<RadarUiState> = _uiState.asStateFlow()

    private val cacheDir = File(context.cacheDir, "radar_frames").apply { mkdirs() }

    suspend fun refreshRadar(showLoadingIndicator: Boolean = false): Result<NowcastEstimate> = withContext(Dispatchers.IO) {
        try {
            if (showLoadingIndicator) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            }

            val activeAddress = preferencesDataStore.getActiveAddress()

            // 1. Consulta a página do IAG para obter total de frames e timestamp da legenda
            val pageInfo = fetchPageMetadata()
            val numFrames = pageInfo.numLayers.coerceIn(5, 12)

            // 2. Faz download dos frames e extrai data/hora real de cada imagem
            val frames = mutableListOf<RadarScanFrame>()
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val baseTime = System.currentTimeMillis()

            for (i in 1..numFrames) {
                val imageUrl = "$BASE_IMAGE_LOOP_URL$i.png"
                val result = downloadOrGetCachedBitmap(
                    frameIndex = i,
                    url = imageUrl,
                    htmlLegenda = if (i == numFrames) pageInfo.latestTimestampText else null
                )

                val parsed = result.parsedTimestamp
                val frameTimeMillis = parsed?.timestampMillis ?: (baseTime - (numFrames - i) * 3 * 60000L)
                val timeLabel = parsed?.formattedLocalTime ?: dateFormat.format(Date(frameTimeMillis))
                val isFrameOutdated = parsed?.isOutdated ?: false
                val fullDateTime = parsed?.formattedFullDateTime ?: timeLabel
                val ageDesc = parsed?.let { RadarTimestampParser.formatDataAgeDescription(it.ageMinutes) } ?: ""

                frames.add(
                    RadarScanFrame(
                        frameIndex = i,
                        url = imageUrl,
                        timestampText = timeLabel,
                        timestampMillis = frameTimeMillis,
                        bitmap = result.bitmap,
                        isOutdated = isFrameOutdated,
                        fullDateTimeText = fullDateTime,
                        dataAgeDescription = ageDesc
                    )
                )
            }

            // 2.1 Aplica supressão de Clutter Estático multitemporal entre os frames da animação
            // para eliminar reflexões permanentes de relevo/torres que ficam estáticas o dia inteiro
            val validBitmaps = frames.mapNotNull { it.bitmap }
            if (validBitmaps.size >= 4) {
                val cleanedBitmaps = RadarAnalysisEngine.suppressStaticClutter(validBitmaps)
                var bmpIdx = 0
                for (idx in frames.indices) {
                    if (frames[idx].bitmap != null && bmpIdx < cleanedBitmaps.size) {
                        frames[idx] = frames[idx].copy(bitmap = cleanedBitmaps[bmpIdx])
                        bmpIdx++
                    }
                }
            }

            val latestBitmap = frames.lastOrNull()?.bitmap
            if (latestBitmap == null) {
                throw IllegalStateException("Não foi possível carregar as imagens do radar IAG-USP.")
            }

            // 2.2 Avalia se as imagens do radar estão desatualizadas (acima de 1 hora)
            val latestFrame = frames.lastOrNull()
            val isDataOutdated = latestFrame?.isOutdated == true
            val dataAgeMinutes = latestFrame?.timestampMillis?.let {
                ((System.currentTimeMillis() - it) / 60000L).coerceAtLeast(0L)
            } ?: 0L
            val dataAgeDescription = latestFrame?.dataAgeDescription?.ifBlank {
                RadarTimestampParser.formatDataAgeDescription(dataAgeMinutes)
            } ?: RadarTimestampParser.formatDataAgeDescription(dataAgeMinutes)
            val fullScanDateTime = latestFrame?.fullDateTimeText?.ifBlank {
                latestFrame.timestampText
            } ?: ""

            val outdatedMessage = if (isDataOutdated) {
                "Imagens do radar IAG-USP desatualizadas há $dataAgeDescription (captura de $fullScanDateTime). O servidor do radar está com atraso de transmissão. Alertas de chuva em tempo real foram suspensos para evitar alarmes falsos."
            } else null

            val cityCoverage = RadarAnalysisEngine.computeCityCoverage(latestBitmap)
            val motion = RadarAnalysisEngine.estimateMotionVector(frames)
            val spDistricts = OpenStreetMapProvider.getDistricts(context)
            val districtStatuses = RadarAnalysisEngine.computeDistrictStatuses(latestBitmap, spDistricts, motion)
            val nowcast = RadarAnalysisEngine.computeNowcast(
                frames = frames,
                userLat = activeAddress.latitude,
                userLon = activeAddress.longitude,
                addressLabel = activeAddress.label,
                isOutdated = isDataOutdated,
                dataAgeMinutes = dataAgeMinutes,
                dataAgeDescription = dataAgeDescription,
                fullScanDateTimeText = fullScanDateTime
            )

            _uiState.value = RadarUiState(
                isLoading = false,
                isRefreshing = false,
                frames = frames,
                activeFrameIndex = frames.size - 1,
                nowcast = nowcast,
                cityCoverage = cityCoverage,
                districtStatuses = districtStatuses,
                activeAddress = activeAddress,
                lastUpdateTime = System.currentTimeMillis(),
                errorMessage = null,
                isDataOutdated = isDataOutdated,
                dataAgeMinutes = dataAgeMinutes,
                dataAgeDescription = dataAgeDescription,
                outdatedWarningMessage = outdatedMessage
            )

            Result.success(nowcast)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Erro ao atualizar radar: ${e.message}", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                errorMessage = "Falha ao conectar com radar IAG-USP: ${e.localizedMessage ?: "Erro de conexão"}"
            )
            Result.failure(e)
        }
    }

    suspend fun updateActiveAddress(address: UserSavedAddress) = withContext(Dispatchers.IO) {
        preferencesDataStore.saveActiveAddress(address)
        val currentFrames = _uiState.value.frames
        if (currentFrames.isNotEmpty()) {
            val latestFrame = currentFrames.lastOrNull()
            val isOutdated = _uiState.value.isDataOutdated || (latestFrame?.isOutdated == true)
            val nowcast = RadarAnalysisEngine.computeNowcast(
                frames = currentFrames,
                userLat = address.latitude,
                userLon = address.longitude,
                addressLabel = address.label,
                isOutdated = isOutdated,
                dataAgeMinutes = _uiState.value.dataAgeMinutes,
                dataAgeDescription = _uiState.value.dataAgeDescription,
                fullScanDateTimeText = latestFrame?.fullDateTimeText ?: latestFrame?.timestampText ?: ""
            )
            _uiState.value = _uiState.value.copy(
                activeAddress = address,
                nowcast = nowcast
            )
        } else {
            refreshRadar(showLoadingIndicator = true)
        }
    }

    fun setActiveFrame(index: Int) {
        val count = _uiState.value.frames.size
        if (count > 0 && index in 0 until count) {
            _uiState.value = _uiState.value.copy(activeFrameIndex = index)
        }
    }

    private data class PageMetadata(
        val numLayers: Int = 10,
        val latestTimestampText: String = ""
    )

    private fun fetchPageMetadata(): PageMetadata {
        return try {
            val request = Request.Builder()
                .url(CHUVA_ONLINE_URL)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: ""

            // Procura data-args="10,9"
            val numLayers: Int
            val argsMatcher = Pattern.compile("data-args=[\"'](\\d+)").matcher(html)
            numLayers = if (argsMatcher.find()) {
                argsMatcher.group(1)?.toIntOrNull() ?: 10
            } else {
                10
            }

            // Procura legenda: <div id="legenda">... 2026-11-07 as 17:36 HL ...</div>
            val legendaMatcher = Pattern.compile("id=[\"']legenda[\"'][^>]*>(.*?)</div>", Pattern.DOTALL).matcher(html)
            var timeText = ""
            if (legendaMatcher.find()) {
                val fullText = legendaMatcher.group(1)?.replace("&nbsp;", " ")?.trim() ?: ""
                timeText = fullText
            }

            PageMetadata(numLayers = numLayers, latestTimestampText = timeText)
        } catch (e: Exception) {
            Log.w(TAG, "Aviso ao obter metadados da página HTML: ${e.message}")
            PageMetadata(numLayers = 10, latestTimestampText = "")
        }
    }

    private data class FrameFetchResult(
        val bitmap: Bitmap?,
        val parsedTimestamp: ParsedRadarTimestamp?
    )

    private fun downloadOrGetCachedBitmap(
        frameIndex: Int,
        url: String,
        htmlLegenda: String? = null
    ): FrameFetchResult {
        val cacheFile = File(cacheDir, "radar_frame_$frameIndex.png")
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val lastModifiedMillis = response.headers.getDate("Last-Modified")?.time
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    FileOutputStream(cacheFile).use { it.write(bytes) }
                    val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (rawBitmap != null) {
                        val parsed = RadarTimestampParser.parseTimestampFromBitmap(
                            bitmap = rawBitmap,
                            headerLastModifiedMillis = lastModifiedMillis,
                            htmlLegendaText = htmlLegenda
                        )
                        val transparentBitmap = makeRadarTransparent(rawBitmap)
                        return FrameFetchResult(transparentBitmap, parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha no download do frame $frameIndex, tentando cache local: ${e.message}")
        }

        // Tenta usar cache em disco se download falhar
        if (cacheFile.exists()) {
            val rawBitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
            if (rawBitmap != null) {
                val parsed = RadarTimestampParser.parseTimestampFromBitmap(
                    bitmap = rawBitmap,
                    headerLastModifiedMillis = cacheFile.lastModified(),
                    htmlLegendaText = htmlLegenda
                )
                val transparentBitmap = makeRadarTransparent(rawBitmap)
                return FrameFetchResult(transparentBitmap, parsed)
            }
        }
        return FrameFetchResult(null, null)
    }

    /**
     * Remove o fundo preto/cinza escuro da imagem do radar IAG-USP, tornando-o transparente
     * para que a camada cartográfica com a cidade de São Paulo (rios, vias e distritos)
     * fique nítida e visível sob os ecos de chuva.
     */
    private fun makeRadarTransparent(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val a = (color shr 24) and 0xFF
            if (a < 30) {
                pixels[i] = 0
                continue
            }

            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val diffRG = Math.abs(r - g)
            val diffGB = Math.abs(g - b)
            val diffRB = Math.abs(r - b)
            val maxDiff = Math.max(Math.max(diffRG, diffGB), diffRB)

            // Tons neutros/escuros (fundo preto do radar, anéis cinza ou borda branca)
            if (maxDiff <= 22 || (r < 40 && g < 40 && b < 40)) {
                pixels[i] = 0 // Transparente
            } else {
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b // Ecos de chuva
            }
        }

        val baseTransparent = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        // Aplica o filtro de despeckle e mitigação de ruídos de solo (ground clutter)
        return RadarAnalysisEngine.filterRadarClutterAndNoise(baseTransparent)
    }
}
