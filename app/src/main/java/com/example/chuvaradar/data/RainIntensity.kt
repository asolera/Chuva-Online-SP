package com.example.chuvaradar.data

import androidx.compose.ui.graphics.Color

/**
 * Categorias de intensidade de chuva do radar meteorológico IAG-USP (36 km).
 */
enum class RainIntensity(
    val label: String,
    val description: String,
    val mmPerHourRange: String,
    val color: Color,
    val hexColorString: String,
    val severityLevel: Int // 0 (sem chuva) a 5 (tempestade)
) {
    NONE(
        label = "Sem Chuva",
        description = "Tempo seco na região",
        mmPerHourRange = "0.0 mm/h",
        color = Color(0xFF64748B),
        hexColorString = "#64748B",
        severityLevel = 0
    ),
    VERY_LIGHT(
        label = "Garoa / Chuvisco",
        description = "Chuva muito fraca, garoa isolada",
        mmPerHourRange = "0.2 - 2.0 mm/h",
        color = Color(0xFF0095FF),
        hexColorString = "#0095FF",
        severityLevel = 1
    ),
    LIGHT(
        label = "Chuva Fraca",
        description = "Chuva leve e contínua",
        mmPerHourRange = "2.0 - 5.0 mm/h",
        color = Color(0xFF22C55E),
        hexColorString = "#22C55E",
        severityLevel = 2
    ),
    MODERATE(
        label = "Chuva Moderada",
        description = "Chuva consistente com poças",
        mmPerHourRange = "5.0 - 15.0 mm/h",
        color = Color(0xFFEAB308),
        hexColorString = "#EAB308",
        severityLevel = 3
    ),
    HEAVY(
        label = "Chuva Forte",
        description = "Chuva pesada, risco de alagamentos",
        mmPerHourRange = "15.0 - 40.0 mm/h",
        color = Color(0xFFEF4444),
        hexColorString = "#EF4444",
        severityLevel = 4
    ),
    INTENSE(
        label = "Chuva Torrencial",
        description = "Tempestade severa / granizo",
        mmPerHourRange = "> 40.0 mm/h",
        color = Color(0xFFA855F7),
        hexColorString = "#A855F7",
        severityLevel = 5
    );

    val isRaining: Boolean
        get() = severityLevel > 0
}
