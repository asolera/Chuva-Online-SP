package com.example.chuvaradar.data

import androidx.compose.ui.graphics.Color

/**
 * Faixas oficiais de operação do Sistema Cantareira (Resolução Conjunta ANA/DAEE).
 */
enum class CantareiraOperationalBand(
    val shortLabel: String,
    val bandLabel: String,
    val color: Color
) {
    NORMAL(
        shortLabel = "Normal",
        bandLabel = "Faixa 1 • Normal",
        color = Color(0xFF10B981)
    ),
    ATTENTION(
        shortLabel = "Atenção",
        bandLabel = "Faixa 2 • Atenção",
        color = Color(0xFFF59E0B)
    ),
    ALERT(
        shortLabel = "Alerta",
        bandLabel = "Faixa 3 • Alerta",
        color = Color(0xFFF97316)
    ),
    RESTRICTION(
        shortLabel = "Restrição",
        bandLabel = "Faixa 4 • Restrição",
        color = Color(0xFFEF4444)
    ),
    SPECIAL(
        shortLabel = "Especial",
        bandLabel = "Faixa 5 • Especial",
        color = Color(0xFFA855F7)
    );

    companion object {
        fun fromVolumePercent(percent: Double): CantareiraOperationalBand = when {
            percent >= 60.0 -> NORMAL
            percent >= 40.0 -> ATTENTION
            percent >= 30.0 -> ALERT
            percent >= 20.0 -> RESTRICTION
            else -> SPECIAL
        }
    }
}

/**
 * Leitura diária oficial do Sistema Cantareira (SABESP Mananciais - local 64).
 */
data class CantareiraDailyPoint(
    val dateIso: String,                  // Ex: "2026-11-23"
    val shortDateLabel: String,           // Ex: "23/11"
    val fullDateLabel: String,            // Ex: "23/11/2026"
    val volumePercent: Double,            // Parâmetro 684: Volume útil armazenado (%)
    val volumeHm3: Double? = null,        // Volume armazenado em hm³ (quando disponível)
    val dailyVariationPercent: Double? = null, // Variação diária (%)
    val rainMm: Double? = null,           // Parâmetro 1: Precipitação diária (mm)
    val monthlyRainMm: Double? = null,    // Chuva acumulada no mês (mm)
    val historicalAvgRainMm: Double? = null // Média histórica do mês (mm)
)

/**
 * Estado da seção do Sistema Cantareira exibida ao final da tela principal.
 */
data class CantareiraUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isFromCache: Boolean = false,
    val points: List<CantareiraDailyPoint> = emptyList(),
    val latest: CantareiraDailyPoint? = null,
    val delta30DaysPercent: Double? = null,
    val minVolume30Days: Double? = null,
    val maxVolume30Days: Double? = null,
    val lastFetchedMillis: Long = 0L,
    val errorMessage: String? = null,
    val sourceUrl: String = OFFICIAL_BASE_DADOS_URL
) {
    val operationalBand: CantareiraOperationalBand?
        get() = latest?.let { CantareiraOperationalBand.fromVolumePercent(it.volumePercent) }

    companion object {
        const val OFFICIAL_BASE_DADOS_URL =
            "https://mananciais.sabesp.com.br/base-dados?locais=64&parametros=684,1&passoTemporal=diario&periodo=1mes"
    }
}
