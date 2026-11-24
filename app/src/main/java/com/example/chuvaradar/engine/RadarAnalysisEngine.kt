package com.example.chuvaradar.engine

import android.graphics.Bitmap
import android.graphics.Color
import com.example.chuvaradar.data.CityRainCoverage
import com.example.chuvaradar.data.DistrictData
import com.example.chuvaradar.data.DistrictDefinition
import com.example.chuvaradar.data.DistrictRainStatus
import com.example.chuvaradar.data.NowcastEstimate
import com.example.chuvaradar.data.RadarScanFrame
import com.example.chuvaradar.data.RainIntensity
import com.example.chuvaradar.data.TimelineProjection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Motor de análise meteorológica, projeção espacial e nowcasting
 * baseado exclusivamente no radar IAG-USP de 36km.
 */
object RadarAnalysisEngine {

    // Limites de coordenadas do radar (OpenLayers do Chuva Online USP)
    const val MIN_LON = -47.134155
    const val MAX_LON = -46.337808
    const val MIN_LAT = -23.930042
    const val MAX_LAT = -23.192604

    // Dimensão padrão do radar da USP
    const val RADAR_PIXEL_SIZE = 1000

    // Constante de conversão média para a latitude de SP: ~0.080 km por pixel
    const val KM_PER_PIXEL = 0.080

    // Conversões Web Mercator (EPSG:3857)
    private val minX = lonToWebMercatorX(MIN_LON)
    private val maxX = lonToWebMercatorX(MAX_LON)
    private val minY = latToWebMercatorY(MIN_LAT)
    private val maxY = latToWebMercatorY(MAX_LAT)

    private fun lonToWebMercatorX(lon: Double): Double = lon * 20037508.34 / 180.0

    private fun latToWebMercatorY(lat: Double): Double {
        val rad = Math.toRadians(lat)
        return ln(tan(PI / 4.0 + rad / 2.0)) * 20037508.34 / PI
    }

    private fun webMercatorXToLon(x: Double): Double = x * 180.0 / 20037508.34

    private fun webMercatorYToLat(y: Double): Double {
        val rad = 2.0 * atan2(Math.exp(y * PI / 20037508.34), 1.0) - PI / 2.0
        return Math.toDegrees(rad)
    }

    /**
     * Converte latitude e longitude em coordenadas de pixel (0..999, 0..999) do radar.
     */
    fun geoToPixel(lat: Double, lon: Double): Pair<Int, Int>? {
        val xM = lonToWebMercatorX(lon)
        val yM = latToWebMercatorY(lat)
        if (xM < minX || xM > maxX || yM < minY || yM > maxY) return null
        val px = ((xM - minX) / (maxX - minX) * RADAR_PIXEL_SIZE).toInt().coerceIn(0, RADAR_PIXEL_SIZE - 1)
        val py = ((maxY - yM) / (maxY - minY) * RADAR_PIXEL_SIZE).toInt().coerceIn(0, RADAR_PIXEL_SIZE - 1)
        return Pair(px, py)
    }

    /**
     * Converte pixel do radar de volta em latitude e longitude WGS84.
     */
    fun pixelToGeo(px: Int, py: Int): Pair<Double, Double> {
        val xM = minX + (px.toDouble() / RADAR_PIXEL_SIZE) * (maxX - minX)
        val yM = maxY - (py.toDouble() / RADAR_PIXEL_SIZE) * (maxY - minY)
        return Pair(webMercatorYToLat(yM), webMercatorXToLon(xM))
    }

    /**
     * Classifica o pixel com base na tabela de refletividade do radar IAG-USP.
     */
    fun classifyColor(colorInt: Int): RainIntensity {
        val alpha = Color.alpha(colorInt)
        if (alpha < 40) return RainIntensity.NONE

        val r = Color.red(colorInt)
        val g = Color.green(colorInt)
        val b = Color.blue(colorInt)

        // Filtra tons de cinza/anéis de distância/textos/linhas de mapa
        val diffRG = kotlin.math.abs(r - g)
        val diffGB = kotlin.math.abs(g - b)
        val diffRB = kotlin.math.abs(r - b)
        val maxDiff = max(max(diffRG, diffGB), diffRB)
        if (maxDiff <= 22 || (r < 40 && g < 40 && b < 40)) {
            return RainIntensity.NONE
        }

        // 1. Chuva torrencial / Granizo (Magenta/Roxo: r alto, b alto, g médio/baixo)
        if (r > 190 && b > 100 && g < 135) {
            return RainIntensity.INTENSE
        }

        // 2. Chuva forte (Vermelho)
        if (r > 130 && g < 65 && b < 65) {
            return RainIntensity.HEAVY
        }

        // 3. Chuva moderada a forte (Laranja / Amarelo)
        if (r > 155 && g > 85 && b < 65) {
            return RainIntensity.MODERATE
        }

        // 4. Chuva fraca a moderada (Verde)
        if ((g > 75 && r < 75 && b < 75) || (g > 150 && b < 75)) {
            return RainIntensity.LIGHT
        }

        // 5. Chuva muito fraca / Garoa (Azul)
        if (b > 95 && r < 70) {
            return RainIntensity.VERY_LIGHT
        }

        return RainIntensity.NONE
    }

    /**
     * Limpa os ruídos de solo (Ground Clutter), artefatos concêntricos da antena e
     * pixels isolados (Speckle Noise) de um frame do radar IAG-USP.
     */
    fun filterRadarClutterAndNoise(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val radarCenterX = 505
        val radarCenterY = 495

        val rainMask = BooleanArray(width * height)
        val intensityMap = ByteArray(width * height)

        for (y in 0 until height) {
            val rowOffset = y * width
            val dy = y - radarCenterY
            val dy2 = dy * dy

            for (x in 0 until width) {
                val idx = rowOffset + x
                val color = pixels[idx]
                val a = (color shr 24) and 0xFF
                if (a < 30) continue

                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF

                // 1. Cone cego / Ground clutter imediato da antena do radar (< 2.2 km ≈ 28 pixels)
                val dx = x - radarCenterX
                val distSq = dx * dx + dy2
                if (distSq < 28 * 28) {
                    pixels[idx] = 0
                    continue
                }

                // 2. Filtro de tons neutros / anéis cinzas / linhas do mapa base do radar IAG
                val diffRG = Math.abs(r - g)
                val diffGB = Math.abs(g - b)
                val diffRB = Math.abs(r - b)
                val maxDiff = Math.max(Math.max(diffRG, diffGB), diffRB)

                // Anéis concêntricos tênues de alcance e rios/costa azul-acinzentados do mapa base
                if (maxDiff <= 24 || (r < 40 && g < 40 && b < 40)) {
                    pixels[idx] = 0
                    continue
                }

                val intensity = classifyColor(color)
                if (intensity.isRaining) {
                    rainMask[idx] = true
                    intensityMap[idx] = intensity.ordinal.toByte()
                } else {
                    pixels[idx] = 0
                }
            }
        }

        // 3. Integral Image (Summed Area Table) para contagem de densidade espacial em O(1)
        val satStride = width + 1
        val sat = IntArray((width + 1) * (height + 1))
        val satModerate = IntArray((width + 1) * (height + 1))

        for (y in 0 until height) {
            var rowSum = 0
            var rowSumMod = 0
            val rowOffset = (y + 1) * satStride
            val prevRowOffset = y * satStride
            val imgOffset = y * width

            for (x in 0 until width) {
                val idx = imgOffset + x
                val isR = if (rainMask[idx]) 1 else 0
                val isMod = if (intensityMap[idx] >= RainIntensity.MODERATE.ordinal) 1 else 0
                rowSum += isR
                rowSumMod += isMod
                sat[rowOffset + (x + 1)] = sat[prevRowOffset + (x + 1)] + rowSum
                satModerate[rowOffset + (x + 1)] = satModerate[prevRowOffset + (x + 1)] + rowSumMod
            }
        }

        fun countRainInBox(cx: Int, cy: Int, r: Int): Int {
            val x1 = Math.max(0, cx - r)
            val y1 = Math.max(0, cy - r)
            val x2 = Math.min(width, cx + r + 1)
            val y2 = Math.min(height, cy + r + 1)
            return sat[y2 * satStride + x2] - sat[y1 * satStride + x2] - sat[y2 * satStride + x1] + sat[y1 * satStride + x1]
        }

        fun countModerateInBox(cx: Int, cy: Int, r: Int): Int {
            val x1 = Math.max(0, cx - r)
            val y1 = Math.max(0, cy - r)
            val x2 = Math.min(width, cx + r + 1)
            val y2 = Math.min(height, cy + r + 1)
            return satModerate[y2 * satStride + x2] - satModerate[y1 * satStride + x2] - satModerate[y2 * satStride + x1] + satModerate[y1 * satStride + x1]
        }

        // 4. Filtro Morfológico de Coesão Espacial (Despeckle & Clutter Removal)
        val cleanedPixels = IntArray(width * height)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val idx = rowOffset + x
                if (!rainMask[idx]) continue

                val intensityOrdinal = intensityMap[idx].toInt()

                // Chuva forte / torrencial (vermelho, magenta)
                if (intensityOrdinal >= RainIntensity.HEAVY.ordinal) {
                    cleanedPixels[idx] = pixels[idx]
                    continue
                }

                // Chuva moderada (amarelo / laranja)
                if (intensityOrdinal == RainIntensity.MODERATE.ordinal) {
                    val count5x5 = countRainInBox(x, y, 2)
                    if (count5x5 >= 4) {
                        cleanedPixels[idx] = pixels[idx]
                    }
                    continue
                }

                // Chuva leve (verde, ~5 mm/h)
                if (intensityOrdinal == RainIntensity.LIGHT.ordinal) {
                    val count5x5 = countRainInBox(x, y, 2)
                    val count7x7 = countRainInBox(x, y, 3)
                    val modCount = countModerateInBox(x, y, 4)

                    // Precisa de pelo menos 6 pixels em 5x5 ou 12 em 7x7 ou ter chuva moderada próxima
                    if (count5x5 >= 6 || count7x7 >= 12 || modCount >= 2) {
                        cleanedPixels[idx] = pixels[idx]
                    }
                    continue
                }

                // Chuva muito fraca / garoa (azul, < 2 mm/h)
                // É o maior responsável por anéis concêntricos falsos e pontilhado de clear-air
                if (intensityOrdinal == RainIntensity.VERY_LIGHT.ordinal) {
                    val count7x7 = countRainInBox(x, y, 3)
                    val count9x9 = countRainInBox(x, y, 4)
                    val modCount = countModerateInBox(x, y, 5)

                    // Requer cluster com volume real (>= 16 pixels em 7x7 ou >= 25 em 9x9 ou chuva mais forte adjacente)
                    if (count7x7 >= 16 || count9x9 >= 25 || modCount >= 1) {
                        cleanedPixels[idx] = pixels[idx]
                    }
                }
            }
        }

        return Bitmap.createBitmap(cleanedPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Filtro multitemporal de Clutter Estático:
     * Identifica e remove pixels que permanecem congelados na mesma posição exata
     * ao longo de praticamente todos os frames da animação (típico de reflexões de relevo/torres).
     */
    fun suppressStaticClutter(frames: List<Bitmap>): List<Bitmap> {
        if (frames.size < 4) return frames

        val width = frames[0].width
        val height = frames[0].height
        val totalFrames = frames.size
        val persistenceCount = IntArray(width * height)

        val framePixelBuffers = frames.map { bmp ->
            val buf = IntArray(width * height)
            bmp.getPixels(buf, 0, width, 0, 0, width, height)
            buf
        }

        for (buf in framePixelBuffers) {
            for (i in buf.indices) {
                if (((buf[i] shr 24) and 0xFF) >= 40) {
                    persistenceCount[i]++
                }
            }
        }

        // Limiar: se o pixel estiver ativo em 85% ou mais dos frames (~30 minutos)
        val staticThreshold = (totalFrames * 0.85).roundToInt().coerceAtLeast(3)

        return framePixelBuffers.map { buf ->
            for (i in buf.indices) {
                if (persistenceCount[i] >= staticThreshold) {
                    buf[i] = 0
                }
            }
            Bitmap.createBitmap(buf, width, height, Bitmap.Config.ARGB_8888)
        }
    }

    data class AreaSample(
        val totalSampled: Int,
        val rainPixelCount: Int,
        val rainFraction: Float,
        val maxIntensity: RainIntensity,
        val dominantIntensity: RainIntensity
    )

    /**
     * Amostra os pixels em torno de um ponto central e calcula estatísticas de chuva.
     */
    fun sampleArea(bitmap: Bitmap, centerPx: Pair<Int, Int>, radiusPx: Int): AreaSample {
        val cx = centerPx.first
        val cy = centerPx.second
        val counts = IntArray(RainIntensity.entries.size)
        var total = 0
        val r2 = radiusPx * radiusPx

        val minX = max(0, cx - radiusPx)
        val maxX = min(bitmap.width - 1, cx + radiusPx)
        val minY = max(0, cy - radiusPx)
        val maxY = min(bitmap.height - 1, cy + radiusPx)

        for (y in minY..maxY) {
            val dy = y - cy
            for (x in minX..maxX) {
                val dx = x - cx
                if (dx * dx + dy * dy <= r2) {
                    val pixel = bitmap.getPixel(x, y)
                    val intensity = classifyColor(pixel)
                    counts[intensity.ordinal]++
                    total++
                }
            }
        }

        if (total == 0) {
            return AreaSample(0, 0, 0f, RainIntensity.NONE, RainIntensity.NONE)
        }

        val rawRainPixels = total - counts[RainIntensity.NONE.ordinal]

        // Exige um número mínimo de pixels (~1.5% da área amostrada ou pelo menos 10 pixels)
        // para confirmar que há chuva real na área, e não apenas 1 ou 2 pixels residuais de ruído
        val minSignificantPixels = max(10, (total * 0.015f).toInt())

        if (rawRainPixels < minSignificantPixels) {
            return AreaSample(
                totalSampled = total,
                rainPixelCount = 0,
                rainFraction = 0f,
                maxIntensity = RainIntensity.NONE,
                dominantIntensity = RainIntensity.NONE
            )
        }

        // maxIntensity exige relevância (mínimo de pixels daquela intensidade)
        var maxIntensity = RainIntensity.NONE
        val minClusterPixels = max(6, (total * 0.012f).toInt())
        for (i in RainIntensity.entries.indices.reversed()) {
            if (counts[i] >= minClusterPixels) {
                maxIntensity = RainIntensity.entries[i]
                break
            }
        }

        var dominant = RainIntensity.NONE
        var dominantCount = 0
        for (i in 1 until RainIntensity.entries.size) {
            if (counts[i] > dominantCount) {
                dominantCount = counts[i]
                dominant = RainIntensity.entries[i]
            }
        }

        if (maxIntensity == RainIntensity.NONE) {
            maxIntensity = dominant
        }

        return AreaSample(
            totalSampled = total,
            rainPixelCount = rawRainPixels,
            rainFraction = rawRainPixels.toFloat() / total.toFloat(),
            maxIntensity = maxIntensity,
            dominantIntensity = dominant
        )
    }

    data class MotionVector(
        val vx: Double, // pixels por minuto
        val vy: Double,
        val speedKmH: Double,
        val directionDegrees: Double,
        val directionLabel: String
    )

    /**
     * Estima o vetor de movimento das massas de chuva comparando frames recentes.
     */
    fun estimateMotionVector(frames: List<RadarScanFrame>): MotionVector {
        val validFrames = frames.filter { it.bitmap != null }
        if (validFrames.size < 2) {
            // Vetor padrão em SP: ventos predominantes de Noroeste/Oeste para Sudeste/Leste (~25 km/h)
            return MotionVector(
                vx = 2.5,
                vy = 1.0,
                speedKmH = 22.0,
                directionDegrees = 110.0,
                directionLabel = "Leste-Sudeste (ESE)"
            )
        }

        // Usamos os últimos frames para calcular o deslocamento do centro de massa
        val fRecent = validFrames.last()
        val fOlder = validFrames[max(0, validFrames.size - 3)] // ~6-9 min atrás

        val cRecent = computeRainCentroid(fRecent.bitmap!!)
        val cOlder = computeRainCentroid(fOlder.bitmap!!)

        val timeDiffMinutes = max(1.0, (fRecent.timestampMillis - fOlder.timestampMillis) / 60000.0)

        if (cRecent != null && cOlder != null) {
            val dx = cRecent.first - cOlder.first
            val dy = cRecent.second - cOlder.second
            val distPx = sqrt(dx * dx + dy * dy)

            // Limite de sanidade física (nuvens não andam a 250 km/h)
            val speedKmH = (distPx * KM_PER_PIXEL / (timeDiffMinutes / 60.0)).coerceIn(5.0, 95.0)
            val vx = (dx / timeDiffMinutes).coerceIn(-15.0, 15.0)
            val vy = (dy / timeDiffMinutes).coerceIn(-15.0, 15.0)

            // Direção meteorológica em graus (0 = Norte, 90 = Leste, 180 = Sul, 270 = Oeste)
            var deg = Math.toDegrees(atan2(dx, -dy))
            if (deg < 0) deg += 360.0

            return MotionVector(
                vx = vx,
                vy = vy,
                speedKmH = speedKmH,
                directionDegrees = deg,
                directionLabel = formatDegreesToCardinal(deg)
            )
        }

        // Fallback realista para São Paulo
        return MotionVector(
            vx = 2.8,
            vy = 0.8,
            speedKmH = 24.0,
            directionDegrees = 105.0,
            directionLabel = "Leste (E)"
        )
    }

    private fun computeRainCentroid(bitmap: Bitmap): Pair<Double, Double>? {
        var sumX = 0.0
        var sumY = 0.0
        var count = 0

        val step = 4 // Amostragem rápida a cada 4 pixels
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val p = bitmap.getPixel(x, y)
                val intensity = classifyColor(p)
                if (intensity.isRaining) {
                    val weight = intensity.severityLevel.toDouble()
                    sumX += x * weight
                    sumY += y * weight
                    count += intensity.severityLevel
                }
            }
        }

        return if (count > 200) {
            Pair(sumX / count, sumY / count)
        } else {
            null
        }
    }

    private fun formatDegreesToCardinal(deg: Double): String {
        val cardinals = arrayOf(
            "Norte (N)", "Norte-Nordeste (NNE)", "Nordeste (NE)", "Leste-Nordeste (ENE)",
            "Leste (E)", "Leste-Sudeste (ESE)", "Sudeste (SE)", "Sul-Sudeste (SSE)",
            "Sul (S)", "Sul-Sudoeste (SSW)", "Sudoeste (SW)", "Oeste-Sudoeste (WSW)",
            "Oeste (W)", "Oeste-Noroeste (WNW)", "Noroeste (NW)", "Norte-Noroeste (NNW)"
        )
        val index = (((deg + 11.25) % 360) / 22.5).toInt()
        return cardinals[index.coerceIn(0, 15)]
    }

    /**
     * Calcula a estimativa de Nowcasting para as coordenadas do usuário.
     */
    fun computeNowcast(
        frames: List<RadarScanFrame>,
        userLat: Double,
        userLon: Double,
        addressLabel: String,
        isOutdated: Boolean = false,
        dataAgeMinutes: Long = 0L,
        dataAgeDescription: String = "",
        fullScanDateTimeText: String = ""
    ): NowcastEstimate {
        val userPx = geoToPixel(userLat, userLon)
        val validFrames = frames.filter { it.bitmap != null }
        val latestFrame = validFrames.lastOrNull()

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val scanTimeText = latestFrame?.timestampText ?: timeFormat.format(Date())

        if (userPx == null || latestFrame?.bitmap == null) {
            return NowcastEstimate(
                addressLabel = addressLabel,
                currentIntensity = RainIntensity.NONE,
                isRainingNow = false,
                estimatedArrivalMinutes = null,
                forecastedIntensity = RainIntensity.NONE,
                confidencePercent = 0,
                statusHeadline = "Local fora da área do radar",
                statusDetail = "O endereço informado está fora do alcance de 36km do radar IAG-USP.",
                speedKmH = 0.0,
                directionLabel = "--",
                directionDegrees = 0.0,
                timeline = emptyList(),
                lastScanTimeText = scanTimeText,
                isDataOutdated = isOutdated,
                dataAgeMinutes = dataAgeMinutes,
                dataAgeDescription = dataAgeDescription,
                fullScanDateTimeText = fullScanDateTimeText
            )
        }

        val latestBitmap = latestFrame.bitmap
        val motion = estimateMotionVector(frames)

        // Se os dados do radar forem antigos (> 1 hora), bloqueia alerta de chuva em tempo real
        if (isOutdated) {
            val headline = "⚠️ Radar IAG-USP Desatualizado"
            val detail = "As imagens do radar são de $fullScanDateTimeText ($dataAgeDescription). " +
                    "O servidor de origem não transmitiu scans recentes na última hora. " +
                    "Alertas automáticos de chuva em tempo real estão suspensos até a normalização do serviço."

            return NowcastEstimate(
                addressLabel = addressLabel,
                currentIntensity = RainIntensity.NONE,
                isRainingNow = false,
                estimatedArrivalMinutes = null,
                forecastedIntensity = RainIntensity.NONE,
                confidencePercent = 0,
                statusHeadline = headline,
                statusDetail = detail,
                speedKmH = motion.speedKmH,
                directionLabel = motion.directionLabel,
                directionDegrees = motion.directionDegrees,
                timeline = emptyList(),
                lastScanTimeText = scanTimeText,
                isDataOutdated = true,
                dataAgeMinutes = dataAgeMinutes,
                dataAgeDescription = dataAgeDescription,
                fullScanDateTimeText = fullScanDateTimeText
            )
        }

        // 1. Condição atual (raio imediato de ~1.5 km ≈ 18 pixels)
        val immediateSample = sampleArea(latestBitmap, userPx, radiusPx = 18)
        val isRainingNow = immediateSample.rainFraction >= 0.15f && immediateSample.maxIntensity.isRaining
        val currentIntensity = if (isRainingNow) immediateSample.dominantIntensity else RainIntensity.NONE

        // 2. Extrapolação ao longo da trajetória inversa (upstream)
        // Se as nuvens se movem a (vx, vy), o que vai atingir o usuário em T minutos está em (userPx.x - vx*T, userPx.y - vy*T)
        var estimatedArrivalMinutes: Int? = null
        var forecastedIntensity = RainIntensity.NONE

        if (isRainingNow) {
            estimatedArrivalMinutes = 0
            forecastedIntensity = currentIntensity
        } else {
            // Testa intervalos de 5 em 5 minutos de 5 até 60 min
            for (t in 5..60 step 5) {
                val upstreamX = (userPx.first - motion.vx * t).roundToInt()
                val upstreamY = (userPx.second - motion.vy * t).roundToInt()

                if (upstreamX in 0 until latestBitmap.width && upstreamY in 0 until latestBitmap.height) {
                    val sample = sampleArea(latestBitmap, Pair(upstreamX, upstreamY), radiusPx = 22)
                    if (sample.rainFraction >= 0.20f && sample.maxIntensity.isRaining) {
                        estimatedArrivalMinutes = t
                        forecastedIntensity = sample.dominantIntensity
                        break
                    }
                }
            }
        }

        // 3. Monta a linha do tempo (timeline): 0, 15, 30, 45, 60 min
        val currentTime = System.currentTimeMillis()
        val timeline = mutableListOf<TimelineProjection>()
        val timeSteps = listOf(0, 15, 30, 45, 60)

        for (minutesAhead in timeSteps) {
            val stepTime = Date(currentTime + minutesAhead * 60000L)
            val timeLabel = timeFormat.format(stepTime)

            val intensity: RainIntensity
            val prob: Int
            val distKm: Double

            if (minutesAhead == 0) {
                intensity = currentIntensity
                prob = if (isRainingNow) 95 else 5
                distKm = 0.0
            } else {
                val upstreamX = (userPx.first - motion.vx * minutesAhead).roundToInt()
                val upstreamY = (userPx.second - motion.vy * minutesAhead).roundToInt()

                if (upstreamX in 0 until latestBitmap.width && upstreamY in 0 until latestBitmap.height) {
                    val sample = sampleArea(latestBitmap, Pair(upstreamX, upstreamY), radiusPx = 22)
                    intensity = sample.dominantIntensity
                    prob = (sample.rainFraction * 100).toInt().coerceIn(0, 100)
                    distKm = (minutesAhead * motion.speedKmH / 60.0)
                } else {
                    intensity = RainIntensity.NONE
                    prob = 0
                    distKm = (minutesAhead * motion.speedKmH / 60.0)
                }
            }

            timeline.add(
                TimelineProjection(
                    minutesAhead = minutesAhead,
                    timeLabel = timeLabel,
                    intensity = intensity,
                    probabilityPercent = prob,
                    distanceKm = distKm
                )
            )
        }

        // 4. Formata status e mensagens amigáveis
        val confidence = when {
            isRainingNow -> 95
            estimatedArrivalMinutes != null && estimatedArrivalMinutes <= 25 -> 88
            estimatedArrivalMinutes != null && estimatedArrivalMinutes <= 45 -> 78
            estimatedArrivalMinutes != null -> 65
            else -> 85
        }

        val headline: String
        val detail: String

        if (isRainingNow) {
            headline = "🌧️ Chovendo agora no seu local"
            detail = "${currentIntensity.label} detectada no radar sobre $addressLabel. " +
                    "Nuvens deslocando a ${motion.speedKmH.roundToInt()} km/h rumo a ${motion.directionLabel}."
        } else if (estimatedArrivalMinutes != null) {
            headline = "🌧️ Chuva prevista em ~$estimatedArrivalMinutes min"
            detail = "O radar IAG-USP detectou aproximação de ${forecastedIntensity.label} avançando para seu endereço. " +
                    "Velocidade de avanço: ${motion.speedKmH.roundToInt()} km/h."
        } else {
            headline = "☀️ Sem chuva prevista em breve"
            detail = "Não há núcleos significativos de chuva em trajetória de colisão com seu endereço para os próximos 45–60 minutos."
        }

        return NowcastEstimate(
            addressLabel = addressLabel,
            currentIntensity = currentIntensity,
            isRainingNow = isRainingNow,
            estimatedArrivalMinutes = estimatedArrivalMinutes,
            forecastedIntensity = forecastedIntensity,
            confidencePercent = confidence,
            statusHeadline = headline,
            statusDetail = detail,
            speedKmH = motion.speedKmH,
            directionLabel = motion.directionLabel,
            directionDegrees = motion.directionDegrees,
            timeline = timeline,
            lastScanTimeText = scanTimeText,
            isDataOutdated = false,
            dataAgeMinutes = dataAgeMinutes,
            dataAgeDescription = dataAgeDescription,
            fullScanDateTimeText = fullScanDateTimeText
        )
    }

    /**
     * Calcula a cobertura percentual de chuva em toda a área metropolitana monitorada.
     */
    fun computeCityCoverage(bitmap: Bitmap): CityRainCoverage {
        var totalPixels = 0
        var veryLight = 0
        var light = 0
        var moderate = 0
        var heavyOrIntense = 0

        // Amostra a cada 3 pixels para desempenho ultra-rápido
        val step = 3
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                totalPixels++
                val pixel = bitmap.getPixel(x, y)
                when (classifyColor(pixel)) {
                    RainIntensity.VERY_LIGHT -> veryLight++
                    RainIntensity.LIGHT -> light++
                    RainIntensity.MODERATE -> moderate++
                    RainIntensity.HEAVY, RainIntensity.INTENSE -> heavyOrIntense++
                    RainIntensity.NONE -> {}
                }
            }
        }

        if (totalPixels == 0) totalPixels = 1
        val totalRaining = veryLight + light + moderate + heavyOrIntense
        val totalPercent = (totalRaining.toFloat() / totalPixels.toFloat()) * 100f
        val veryLightPercent = (veryLight.toFloat() / totalPixels.toFloat()) * 100f
        val lightPercent = (light.toFloat() / totalPixels.toFloat()) * 100f
        val moderatePercent = (moderate.toFloat() / totalPixels.toFloat()) * 100f
        val heavyPercent = (heavyOrIntense.toFloat() / totalPixels.toFloat()) * 100f

        val summary = when {
            heavyPercent > 5f -> "Tempestades e chuvas fortes em partes da Grande SP"
            totalPercent > 35f -> "Chuva ampla atingindo mais de 1/3 da Grande São Paulo"
            totalPercent > 15f -> "Pancadas isoladas e chuvas em áreas pontuais"
            totalPercent > 2f -> "Chuvas esparsas ou garoa localizada"
            else -> "Grande São Paulo predominantemente sem chuva no momento"
        }

        return CityRainCoverage(
            totalRainPercent = totalPercent,
            veryLightPercent = veryLightPercent,
            lightPercent = lightPercent,
            moderatePercent = moderatePercent,
            heavyOrIntensePercent = heavyPercent,
            summaryText = summary
        )
    }

    /**
     * Calcula o status de chuva individual para cada distrito oficial de São Paulo com seus polígonos,
     * incluindo a estimativa atual e a projeção de nowcasting para daqui a 60 minutos.
     */
    fun computeDistrictStatuses(
        bitmap: Bitmap,
        customDistricts: List<DistrictDefinition>? = null,
        motionVector: MotionVector? = null
    ): List<DistrictRainStatus> {
        val targets = customDistricts ?: DistrictData.defaultDistricts
        return targets.map { def ->
            val px = geoToPixel(def.lat, def.lon)
            if (px == null) {
                DistrictRainStatus(
                    id = def.id,
                    name = def.name,
                    zone = def.zone,
                    centroidLat = def.lat,
                    centroidLon = def.lon,
                    rainPercent = 0f,
                    dominantIntensity = RainIntensity.NONE,
                    maxIntensity = RainIntensity.NONE,
                    cgeAlertStatus = "Normal",
                    polygons = def.polygons
                )
            } else {
                val radiusPx = (def.radiusKm / KM_PER_PIXEL).roundToInt().coerceIn(12, 45)
                val sample = sampleArea(bitmap, px, radiusPx)
                val rawRainPercent = sample.rainFraction * 100f
                // Filtro de relevância meteorológica:
                // Se for apenas garoa/chuva muito fraca (VERY_LIGHT), requer pelo menos 14% de cobertura na área
                // Se for chuva leve ou mais, requer pelo menos 8% de cobertura na área
                val isEffectivelyRaining = when (sample.maxIntensity) {
                    RainIntensity.VERY_LIGHT -> rawRainPercent >= 14f
                    RainIntensity.LIGHT, RainIntensity.MODERATE, RainIntensity.HEAVY, RainIntensity.INTENSE -> rawRainPercent >= 8f
                    else -> false
                }

                val effectiveIntensity = if (isEffectivelyRaining) sample.maxIntensity else RainIntensity.NONE
                val effectivePercent = if (isEffectivelyRaining) rawRainPercent else 0f

                val alertStatus = when {
                    effectiveIntensity == RainIntensity.INTENSE || effectiveIntensity == RainIntensity.HEAVY -> "Alerta"
                    effectivePercent > 35f -> "Atenção"
                    effectivePercent > 12f -> "Atenção"
                    else -> "Normal"
                }

                // Projeções meteorológicas para +30 min e +60 min (deslocamento pelo vetor de vento)
                var fc30Percent = 0f
                var fc30Intensity = RainIntensity.NONE
                var fc30Alert = "Normal"

                var fc60Percent = 0f
                var fc60Intensity = RainIntensity.NONE
                var fc60Alert = "Normal"

                if (motionVector != null) {
                    // +30 minutos
                    val upstreamX30 = (px.first - motionVector.vx * 30).roundToInt()
                    val upstreamY30 = (px.second - motionVector.vy * 30).roundToInt()
                    if (upstreamX30 in 0 until bitmap.width && upstreamY30 in 0 until bitmap.height) {
                        val sample30 = sampleArea(bitmap, Pair(upstreamX30, upstreamY30), radiusPx)
                        val rawPct30 = sample30.rainFraction * 100f
                        val isRain30 = when (sample30.maxIntensity) {
                            RainIntensity.VERY_LIGHT -> rawPct30 >= 14f
                            RainIntensity.LIGHT, RainIntensity.MODERATE, RainIntensity.HEAVY, RainIntensity.INTENSE -> rawPct30 >= 8f
                            else -> false
                        }
                        if (isRain30) {
                            fc30Percent = rawPct30
                            fc30Intensity = sample30.maxIntensity
                            fc30Alert = when {
                                sample30.maxIntensity == RainIntensity.INTENSE || sample30.maxIntensity == RainIntensity.HEAVY -> "Alerta"
                                fc30Percent > 35f -> "Atenção"
                                fc30Percent > 12f -> "Atenção"
                                else -> "Normal"
                            }
                        }
                    }

                    // +60 minutos
                    val upstreamX60 = (px.first - motionVector.vx * 60).roundToInt()
                    val upstreamY60 = (px.second - motionVector.vy * 60).roundToInt()
                    if (upstreamX60 in 0 until bitmap.width && upstreamY60 in 0 until bitmap.height) {
                        val sample60 = sampleArea(bitmap, Pair(upstreamX60, upstreamY60), radiusPx)
                        val rawPct60 = sample60.rainFraction * 100f
                        val isRain60 = when (sample60.maxIntensity) {
                            RainIntensity.VERY_LIGHT -> rawPct60 >= 14f
                            RainIntensity.LIGHT, RainIntensity.MODERATE, RainIntensity.HEAVY, RainIntensity.INTENSE -> rawPct60 >= 8f
                            else -> false
                        }
                        if (isRain60) {
                            fc60Percent = rawPct60
                            fc60Intensity = sample60.maxIntensity
                            fc60Alert = when {
                                sample60.maxIntensity == RainIntensity.INTENSE || sample60.maxIntensity == RainIntensity.HEAVY -> "Alerta"
                                fc60Percent > 35f -> "Atenção"
                                fc60Percent > 12f -> "Atenção"
                                else -> "Normal"
                            }
                        }
                    }
                }

                DistrictRainStatus(
                    id = def.id,
                    name = def.name,
                    zone = def.zone,
                    centroidLat = def.lat,
                    centroidLon = def.lon,
                    rainPercent = effectivePercent,
                    dominantIntensity = if (isEffectivelyRaining) sample.dominantIntensity else RainIntensity.NONE,
                    maxIntensity = effectiveIntensity,
                    cgeAlertStatus = alertStatus,
                    polygons = def.polygons,
                    forecast30mRainPercent = fc30Percent,
                    forecast30mIntensity = fc30Intensity,
                    forecast30mAlertStatus = fc30Alert,
                    forecast60mRainPercent = fc60Percent,
                    forecast60mIntensity = fc60Intensity,
                    forecast60mAlertStatus = fc60Alert
                )
            }
        }
    }
}
