package com.example.chuvaradar.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser resiliente para os retornos JSON oficiais do portal Mananciais da SABESP
 * (tanto API v4 quanto API clássica ResumoSistemas) e para o cache local em disco.
 */
object SabespCantareiraParser {

    const val CANTAREIRA_V4_SYSTEM_ID = 64

    /**
     * Faz parse de um registro diário do Sistema Cantareira a partir do JSON da SABESP.
     * Suporta:
     * 1) API v4: { "data": [ { "idSistema": 64, "volumeUtilArmazenadoPorcentagem": ..., ... } ] }
     *    ou { "sistemas": [...] } ou array direto [ ... ]
     * 2) API clássica: { "ReturnObj": { "sistemas": [ { "Nome": "Cantareira", "VolumePorcentagem": ..., ... } ] } }
     */
    fun parseDailyResponse(jsonText: String, requestedDateIso: String): CantareiraDailyPoint? {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return null

        val systemsArray: JSONArray? = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                extractSystemsArray(root)
            }
            else -> null
        }

        if (systemsArray != null) {
            for (i in 0 until systemsArray.length()) {
                val obj = systemsArray.optJSONObject(i) ?: continue
                if (isCantareiraSystem(obj)) {
                    val point = parseSystemObject(obj, requestedDateIso)
                    if (point != null) return point
                }
            }
        } else if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            if (isCantareiraSystem(root)) {
                return parseSystemObject(root, requestedDateIso)
            }
        }

        return null
    }

    /**
     * Faz parse de uma resposta em série temporal (lista de leituras diárias do Cantareira).
     */
    fun parseTimeSeriesResponse(jsonText: String): List<CantareiraDailyPoint> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val itemsArray: JSONArray? = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("data")
                    ?: root.optJSONArray("dados")
                    ?: root.optJSONArray("items")
                    ?: root.optJSONArray("series")
            }
            else -> null
        }

        if (itemsArray == null) return emptyList()

        val results = mutableListOf<CantareiraDailyPoint>()
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.optJSONObject(i) ?: continue
            // Se houver idSistema/nome explícito de outro sistema, ignora
            if (hasOtherSystemIdentifier(obj)) continue

            val rawDate = obj.optString("data")
                .ifBlank { obj.optString("dataMedicao") }
                .ifBlank { obj.optString("date") }
                .ifBlank { obj.optString("dia") }
            val dateIso = normalizeDateToIso(rawDate) ?: continue
            val point = parseSystemObject(obj, dateIso)
            if (point != null) {
                results.add(point)
            }
        }

        return results.distinctBy { it.dateIso }.sortedBy { it.dateIso }
    }

    private fun extractSystemsArray(root: JSONObject): JSONArray? {
        root.optJSONArray("data")?.let { return it }
        root.optJSONArray("sistemas")?.let { return it }
        root.optJSONArray("Sistemas")?.let { return it }

        val returnObj = root.optJSONObject("ReturnObj") ?: root.optJSONObject("returnObj")
        if (returnObj != null) {
            returnObj.optJSONArray("sistemas")?.let { return it }
            returnObj.optJSONArray("Sistemas")?.let { return it }
            returnObj.optJSONArray("data")?.let { return it }
        }

        val dataObj = root.optJSONObject("data")
        if (dataObj != null) {
            dataObj.optJSONArray("sistemas")?.let { return it }
            dataObj.optJSONArray("Sistemas")?.let { return it }
        }

        return null
    }

    private fun isCantareiraSystem(obj: JSONObject): Boolean {
        val idKeys = listOf("idSistema", "IdSistema", "idLocal", "id")
        for (key in idKeys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val idVal = obj.optInt(key, -1)
                if (idVal == CANTAREIRA_V4_SYSTEM_ID) return true
            }
        }

        val nameKeys = listOf("nome", "Nome", "sistema", "Sistema", "local", "descricao")
        for (key in nameKeys) {
            val name = obj.optString(key, "")
            if (name.contains("cantareira", ignoreCase = true)) {
                return true
            }
        }

        // Na API clássica ResumoSistemas, SistemaId == 0 corresponde ao Sistema Cantareira
        if (obj.has("SistemaId") && obj.optInt("SistemaId", -1) == 0) {
            val nome = obj.optString("Nome", "")
            if (nome.isBlank() || nome.contains("cantareira", ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    private fun hasOtherSystemIdentifier(obj: JSONObject): Boolean {
        val idVal = when {
            obj.has("idSistema") -> obj.optInt("idSistema", -1)
            obj.has("idLocal") -> obj.optInt("idLocal", -1)
            else -> -1
        }
        if (idVal > 0 && idVal != CANTAREIRA_V4_SYSTEM_ID) return true

        val nome = obj.optString("nome").ifBlank { obj.optString("Nome") }
        if (nome.isNotBlank() && !nome.contains("cantareira", ignoreCase = true)) return true

        return false
    }

    private fun parseSystemObject(obj: JSONObject, fallbackDateIso: String): CantareiraDailyPoint? {
        val volumePercent = readFirstDouble(
            obj,
            "volumeUtilArmazenadoPorcentagem",
            "volumePorcentagem",
            "VolumePorcentagem",
            "volumeOperacionalPorcentagem",
            "volumeArmazenadoPorcentagem",
            "VolumePorcentagemAR",
            "volumeUtilPorcentagem",
            "valorVolumePorcentagem",
            "684"
        ) ?: return null

        // Sanity check: Cantareira % fica em faixa razoável (-30%..130% contando reserva técnica antiga ou extravasamento)
        if (volumePercent.isNaN() || volumePercent.isInfinite() || volumePercent < -30.0 || volumePercent > 130.0) {
            return null
        }

        val volumeHm3 = readFirstDouble(
            obj,
            "volumeUtilArmazenadoHm3",
            "volumeArmazenadoHm3",
            "volumeOperacionalHm3",
            "volumeOperacional",
            "VolumeOperacional",
            "volumeArmazenado"
        )

        val dailyVariation = readFirstDouble(
            obj,
            "variacaoVolumeUtil",
            "variacaoVolumePorcentagem",
            "variacaoVolume",
            "VolumeVariacaoNum",
            "VolumeVariacaoStr"
        )

        val rainMm = readFirstDouble(
            obj,
            "chuva",
            "chuvaDiaria",
            "precipitacao",
            "precipitacaoDia",
            "PrecDia",
            "precDia",
            "1"
        )

        val monthlyRain = readFirstDouble(
            obj,
            "chuvaAcumuladaNoMes",
            "chuvaAcumuladaMes",
            "precipitacaoMensal",
            "PrecMensal",
            "precMensal"
        )

        val historicalAvgRain = readFirstDouble(
            obj,
            "chuvaMediaHistorica",
            "mediaHistoricaMes",
            "PrecHist",
            "precHist"
        )

        val rawDate = obj.optString("data")
            .ifBlank { obj.optString("dataMedicao") }
            .ifBlank { obj.optString("DataString") }
        val resolvedDateIso = normalizeDateToIso(rawDate) ?: fallbackDateIso
        val (shortLabel, fullLabel) = formatLabelsFromIso(resolvedDateIso)

        return CantareiraDailyPoint(
            dateIso = resolvedDateIso,
            shortDateLabel = shortLabel,
            fullDateLabel = fullLabel,
            volumePercent = volumePercent,
            volumeHm3 = volumeHm3,
            dailyVariationPercent = dailyVariation,
            rainMm = rainMm,
            monthlyRainMm = monthlyRain,
            historicalAvgRainMm = historicalAvgRain
        )
    }

    private fun readFirstDouble(obj: JSONObject, vararg keys: String): Double? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val raw = obj.opt(key)
            val parsed = parseFlexibleDouble(raw)
            if (parsed != null) return parsed
        }
        return null
    }

    fun parseFlexibleDouble(raw: Any?): Double? {
        return when (raw) {
            null -> null
            is Number -> {
                val d = raw.toDouble()
                if (d.isNaN() || d.isInfinite()) null else d
            }
            is String -> {
                val cleaned = raw.trim()
                    .replace("%", "")
                    .replace("mm", "", ignoreCase = true)
                    .replace("hm³", "", ignoreCase = true)
                    .replace(" ", "")
                    .replace(",", ".")
                if (cleaned.isEmpty() || cleaned == "-" || cleaned == "--" || cleaned.equals("null", ignoreCase = true)) {
                    null
                } else {
                    cleaned.toDoubleOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
                }
            }
            else -> null
        }
    }

    fun normalizeDateToIso(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        // Formato ISO: "2026-11-23" ou "2026-11-23T00:00:00"
        val isoRegex = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")
        isoRegex.find(trimmed)?.let { match ->
            return "${match.groupValues[1]}-${match.groupValues[2]}-${match.groupValues[3]}"
        }

        // Formato brasileiro: "23/11/2026"
        val brRegex = Regex("^(\\d{2})/(\\d{2})/(\\d{4})")
        brRegex.find(trimmed)?.let { match ->
            return "${match.groupValues[3]}-${match.groupValues[2]}-${match.groupValues[1]}"
        }

        return null
    }

    fun formatLabelsFromIso(dateIso: String): Pair<String, String> {
        val parts = dateIso.split("-")
        return if (parts.size == 3) {
            val yyyy = parts[0]
            val mm = parts[1]
            val dd = parts[2]
            "$dd/$mm" to "$dd/$mm/$yyyy"
        } else {
            dateIso to dateIso
        }
    }

    fun serializePoint(point: CantareiraDailyPoint): String {
        val obj = JSONObject().apply {
            put("dateIso", point.dateIso)
            put("volumePercent", point.volumePercent)
            point.volumeHm3?.let { put("volumeHm3", it) }
            point.dailyVariationPercent?.let { put("dailyVariationPercent", it) }
            point.rainMm?.let { put("rainMm", it) }
            point.monthlyRainMm?.let { put("monthlyRainMm", it) }
            point.historicalAvgRainMm?.let { put("historicalAvgRainMm", it) }
        }
        return obj.toString()
    }

    fun deserializePoint(jsonText: String): CantareiraDailyPoint? {
        return try {
            val obj = JSONObject(jsonText)
            val dateIso = obj.optString("dateIso")
            if (dateIso.isBlank() || !obj.has("volumePercent")) return null
            val volumePercent = obj.getDouble("volumePercent")
            val (shortLabel, fullLabel) = formatLabelsFromIso(dateIso)
            CantareiraDailyPoint(
                dateIso = dateIso,
                shortDateLabel = shortLabel,
                fullDateLabel = fullLabel,
                volumePercent = volumePercent,
                volumeHm3 = if (obj.has("volumeHm3")) obj.optDouble("volumeHm3") else null,
                dailyVariationPercent = if (obj.has("dailyVariationPercent")) obj.optDouble("dailyVariationPercent") else null,
                rainMm = if (obj.has("rainMm")) obj.optDouble("rainMm") else null,
                monthlyRainMm = if (obj.has("monthlyRainMm")) obj.optDouble("monthlyRainMm") else null,
                historicalAvgRainMm = if (obj.has("historicalAvgRainMm")) obj.optDouble("historicalAvgRainMm") else null
            )
        } catch (_: Exception) {
            null
        }
    }

    fun serializeSnapshot(points: List<CantareiraDailyPoint>, fetchedMillis: Long): String {
        val root = JSONObject()
        root.put("fetchedMillis", fetchedMillis)
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(JSONObject(serializePoint(p)))
        }
        root.put("points", arr)
        return root.toString()
    }

    fun deserializeSnapshot(jsonText: String): Pair<Long, List<CantareiraDailyPoint>>? {
        return try {
            val root = JSONObject(jsonText)
            val fetchedMillis = root.optLong("fetchedMillis", 0L)
            val arr = root.optJSONArray("points") ?: return null
            val list = mutableListOf<CantareiraDailyPoint>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                deserializePoint(obj.toString())?.let { list.add(it) }
            }
            if (list.isEmpty()) null else fetchedMillis to list.sortedBy { it.dateIso }
        } catch (_: Exception) {
            null
        }
    }
}
