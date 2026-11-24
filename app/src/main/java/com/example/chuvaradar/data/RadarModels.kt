package com.example.chuvaradar.data

import android.graphics.Bitmap

/**
 * Representa um frame de imagem da animação de radar da USP (36km).
 */
data class RadarScanFrame(
    val frameIndex: Int,          // 1..10 (1 é o mais antigo ~27min atrás, 10 é o mais recente)
    val url: String,
    val timestampText: String,    // Ex: "17:30"
    val timestampMillis: Long,
    val bitmap: Bitmap? = null,
    val rainPixelCount: Int = 0,
    val isOutdated: Boolean = false,
    val fullDateTimeText: String = "",
    val dataAgeDescription: String = ""
)

/**
 * Projeção pontual para um horizonte de tempo futuro (ex: +15m, +30m).
 */
data class TimelineProjection(
    val minutesAhead: Int,        // 0, 15, 30, 45, 60
    val timeLabel: String,        // Ex: "12:15"
    val intensity: RainIntensity,
    val probabilityPercent: Int,  // 0..100
    val distanceKm: Double        // Distância estimada da massa de chuva
)

/**
 * Resultado da estimativa de nowcasting para o endereço do usuário.
 */
data class NowcastEstimate(
    val addressLabel: String,
    val currentIntensity: RainIntensity,
    val isRainingNow: Boolean,
    val estimatedArrivalMinutes: Int?, // null se sem chuva prevista, 0 se chovendo agora, ou 5..60
    val forecastedIntensity: RainIntensity,
    val confidencePercent: Int,
    val statusHeadline: String,        // Ex: "🌧️ Chuva moderada em ~20 min"
    val statusDetail: String,          // Explicativo detalhado
    val speedKmH: Double,              // Velocidade de deslocamento das nuvens
    val directionLabel: String,        // Ex: "Leste-Nordeste (75°)"
    val directionDegrees: Double,
    val timeline: List<TimelineProjection>,
    val lastScanTimeText: String,
    val isDataOutdated: Boolean = false,
    val dataAgeMinutes: Long = 0L,
    val dataAgeDescription: String = "",
    val fullScanDateTimeText: String = ""
)

/**
 * Percentual da Grande São Paulo sob chuva.
 */
data class CityRainCoverage(
    val totalRainPercent: Float,     // Ex: 28.5%
    val veryLightPercent: Float,
    val lightPercent: Float,
    val moderatePercent: Float,
    val heavyOrIntensePercent: Float,
    val summaryText: String
)

/**
 * Status meteorológico e camada visual de um distrito/região da cidade.
 */
data class DistrictRainStatus(
    val id: String,
    val name: String,
    val zone: String,                // "Centro", "Zona Norte", "Zona Sul", "Zona Leste", "Zona Oeste", "Grande SP"
    val centroidLat: Double,
    val centroidLon: Double,
    val rainPercent: Float,          // 0..100% da área do distrito sob chuva agora
    val dominantIntensity: RainIntensity,
    val maxIntensity: RainIntensity,
    val cgeAlertStatus: String,      // "Normal", "Atenção", "Alerta", "Emergência"
    val polygons: List<List<Pair<Double, Double>>> = emptyList(), // Vértices do shapefile real do distrito
    val forecast30mRainPercent: Float = 0f,
    val forecast30mIntensity: RainIntensity = RainIntensity.NONE,
    val forecast30mAlertStatus: String = "Normal",
    val forecast60mRainPercent: Float = 0f,
    val forecast60mIntensity: RainIntensity = RainIntensity.NONE,
    val forecast60mAlertStatus: String = "Normal"
)

/**
 * Endereço configurado pelo usuário.
 */
data class UserSavedAddress(
    val id: String,
    val label: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double,
    val isSelected: Boolean = false
)
