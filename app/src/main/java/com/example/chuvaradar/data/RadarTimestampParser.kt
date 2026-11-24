package com.example.chuvaradar.data

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ParsedRadarTimestamp(
    val timestampMillis: Long,
    val formattedLocalTime: String,
    val formattedFullDateTime: String,
    val rawText: String,
    val isOutdated: Boolean,
    val ageMinutes: Long
)

object RadarTimestampParser {
    private const val TAG = "RadarTimestampParser"
    private const val OUTDATED_THRESHOLD_MINUTES = 60L // 1 hora

    // Regex para capturar data e hora na imagem do radar da USP
    // Ex: "2026-11-07 as 20:30 UTC" ou "2026-11-07 às 20:30 UTC" ou "2026-11-07 20:30"
    private val IMAGE_TIMESTAMP_PATTERN = Pattern.compile(
        "(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\s*(?:as|às|at|@)?\\s*(\\d{1,2}):(\\d{2})(?:\\s*(UTC|HL|BRT|GMT))?",
        Pattern.CASE_INSENSITIVE
    )

    // Regex para fallback da legenda HTML: "2026-11-07 as 17:36 HL"
    private val HTML_LEGENDA_PATTERN = Pattern.compile(
        "(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\s*(?:as|às|at|@)?\\s*(\\d{1,2}):(\\d{2})\\s*(HL|UTC|BRT)?",
        Pattern.CASE_INSENSITIVE
    )

    private val localTimeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    private val utcTimeZone = TimeZone.getTimeZone("UTC")

    /**
     * Tenta extrair a data e hora presentes na imagem usando OCR do ML Kit.
     * Na imagem do radar IAG-USP, a etiqueta com timestamp fica no canto superior esquerdo.
     */
    fun parseTimestampFromBitmap(
        bitmap: Bitmap,
        headerLastModifiedMillis: Long? = null,
        htmlLegendaText: String? = null
    ): ParsedRadarTimestamp {
        var recognizedText: String? = null

        try {
            // Recorta a região superior esquerda da imagem (onde fica a caixa de texto cinza do timestamp)
            val cropWidth = (bitmap.width * 0.45f).toInt().coerceIn(100, bitmap.width)
            val cropHeight = (bitmap.height * 0.08f).toInt().coerceIn(35, bitmap.height)
            val cropBitmap = Bitmap.createBitmap(bitmap, 0, 0, cropWidth, cropHeight)

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(cropBitmap, 0)
            val result = Tasks.await(recognizer.process(inputImage), 3, TimeUnit.SECONDS)
            recognizedText = result.text.trim()
            Log.d(TAG, "Texto detectado na imagem pelo OCR: '$recognizedText'")
        } catch (e: Exception) {
            Log.w(TAG, "OCR na imagem não disponível ou falhou: ${e.message}")
        }

        // 1. Tenta interpretar o texto extraído da imagem
        if (!recognizedText.isNullOrBlank()) {
            val parsedFromOcr = parseDateString(recognizedText, defaultIsUtc = true)
            if (parsedFromOcr != null) {
                return buildResult(parsedFromOcr, recognizedText)
            }
        }

        // 2. Fallback: Legenda da página HTML do IAG (chuvaonline.iag.usp.br)
        if (!htmlLegendaText.isNullOrBlank()) {
            val parsedFromHtml = parseHtmlLegenda(htmlLegendaText)
            if (parsedFromHtml != null) {
                Log.d(TAG, "Usando timestamp da legenda HTML do IAG: $parsedFromHtml")
                return buildResult(parsedFromHtml, htmlLegendaText)
            }
        }

        // 3. Fallback: Header HTTP Last-Modified da imagem
        if (headerLastModifiedMillis != null && headerLastModifiedMillis > 0L) {
            Log.d(TAG, "Usando timestamp do cabeçalho Last-Modified: $headerLastModifiedMillis")
            return buildResult(headerLastModifiedMillis, "HTTP Last-Modified")
        }

        // 4. Último recurso (hora do sistema atual)
        val now = System.currentTimeMillis()
        return buildResult(now, "Tempo real estimado")
    }

    /**
     * Interpreta strings de data como "2026-11-07 as 20:30 UTC" ou "2026-11-07 17:30".
     */
    fun parseDateString(rawText: String, defaultIsUtc: Boolean = true): Long? {
        val matcher = IMAGE_TIMESTAMP_PATTERN.matcher(rawText)
        if (matcher.find()) {
            val year = matcher.group(1)?.toIntOrNull() ?: return null
            val month = matcher.group(2)?.toIntOrNull() ?: return null
            val day = matcher.group(3)?.toIntOrNull() ?: return null
            val hour = matcher.group(4)?.toIntOrNull() ?: return null
            val minute = matcher.group(5)?.toIntOrNull() ?: return null
            val tzStr = matcher.group(6)?.uppercase(Locale.ROOT)

            val isUtc = when (tzStr) {
                "UTC", "GMT" -> true
                "HL", "BRT" -> false
                else -> defaultIsUtc
            }

            val calendar = java.util.Calendar.getInstance(if (isUtc) utcTimeZone else localTimeZone)
            calendar.clear()
            calendar.set(year, month - 1, day, hour, minute, 0)
            return calendar.timeInMillis
        }
        return null
    }

    /**
     * Interpreta legenda do chuvaonline HTML: ex "2026-11-07 as 17:36 HL"
     */
    fun parseHtmlLegenda(htmlText: String): Long? {
        val matcher = HTML_LEGENDA_PATTERN.matcher(htmlText)
        if (matcher.find()) {
            val year = matcher.group(1)?.toIntOrNull() ?: return null
            val month = matcher.group(2)?.toIntOrNull() ?: return null
            val day = matcher.group(3)?.toIntOrNull() ?: return null
            val hour = matcher.group(4)?.toIntOrNull() ?: return null
            val minute = matcher.group(5)?.toIntOrNull() ?: return null
            val tzStr = matcher.group(6)?.uppercase(Locale.ROOT)

            val isUtc = (tzStr == "UTC" || tzStr == "GMT")
            val calendar = java.util.Calendar.getInstance(if (isUtc) utcTimeZone else localTimeZone)
            calendar.clear()
            calendar.set(year, month - 1, day, hour, minute, 0)
            return calendar.timeInMillis
        }
        return null
    }

    private fun buildResult(timestampMillis: Long, rawText: String): ParsedRadarTimestamp {
        val now = System.currentTimeMillis()
        val ageMillis = now - timestampMillis
        val ageMinutes = (ageMillis / 60000L).coerceAtLeast(0L)
        val isOutdated = ageMinutes >= OUTDATED_THRESHOLD_MINUTES

        val localDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = localTimeZone
        }
        val fullDateTimeFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).apply {
            timeZone = localTimeZone
        }

        val dateObj = Date(timestampMillis)
        val formattedLocal = localDateFormat.format(dateObj)
        val formattedFull = fullDateTimeFormat.format(dateObj)

        return ParsedRadarTimestamp(
            timestampMillis = timestampMillis,
            formattedLocalTime = formattedLocal,
            formattedFullDateTime = formattedFull,
            rawText = rawText,
            isOutdated = isOutdated,
            ageMinutes = ageMinutes
        )
    }

    /**
     * Formata um texto amigável de idade dos dados para o usuário.
     * Ex: "há 2 horas", "há 2 dias", "há 15 minutos".
     */
    fun formatDataAgeDescription(ageMinutes: Long): String {
        return when {
            ageMinutes < 2 -> "agora mesmo"
            ageMinutes < 60 -> "há $ageMinutes min"
            ageMinutes < 120 -> "há ~1 hora"
            ageMinutes < 1440 -> {
                val hours = ageMinutes / 60
                "há $hours horas"
            }
            else -> {
                val days = ageMinutes / 1440
                if (days == 1L) "há 1 dia" else "há $days dias"
            }
        }
    }
}
