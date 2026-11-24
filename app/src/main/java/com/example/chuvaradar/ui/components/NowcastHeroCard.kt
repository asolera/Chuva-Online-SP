package com.example.chuvaradar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chuvaradar.data.CityRainCoverage
import com.example.chuvaradar.data.NowcastEstimate
import com.example.chuvaradar.data.RainIntensity
import com.example.chuvaradar.data.TimelineProjection
import kotlin.math.roundToInt

@Composable
fun NowcastHeroCard(
    nowcast: NowcastEstimate?,
    cityCoverage: CityRainCoverage?,
    onAddressClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOutdated = nowcast?.isDataOutdated == true
    val currentIntensity = nowcast?.currentIntensity ?: RainIntensity.NONE
    val forecastedIntensity = nowcast?.forecastedIntensity ?: RainIntensity.NONE
    val isRaining = nowcast?.isRainingNow == true
    val hasForecastRain = nowcast?.estimatedArrivalMinutes != null && !isRaining

    val accentColor by animateColorAsState(
        targetValue = when {
            isOutdated -> Color(0xFFF59E0B)
            isRaining -> currentIntensity.color
            hasForecastRain -> forecastedIntensity.color
            else -> Color(0xFF38BDF8)
        },
        animationSpec = tween(400),
        label = "accentColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("nowcast_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F1E36)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (isOutdated) 0.22f else 0.15f),
                            Color(0xFF0B192C)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            // Top Row: Address chip + Radar scan timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Address Chip (Clickable to change address)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .clickable(onClick = onAddressClick)
                        .testTag("change_address_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditLocation,
                            contentDescription = "Editar Endereço",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = nowcast?.addressLabel ?: "Definir endereço",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

                // Radar Timestamp Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOutdated) Color(0xFF451A03).copy(alpha = 0.8f) else Color(0xFF070F1E),
                    border = if (isOutdated) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)) else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isOutdated) Color(0xFFF59E0B) else Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOutdated) "Radar: ${nowcast?.lastScanTimeText ?: "--:--"} (${nowcast?.dataAgeDescription ?: "desatualizado"})"
                                   else "Radar: ${nowcast?.lastScanTimeText ?: "--:--"}",
                            fontSize = 11.sp,
                            fontWeight = if (isOutdated) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isOutdated) Color(0xFFFDE68A) else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Status Headline
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nowcast?.statusHeadline ?: "Avaliando dados do radar...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = nowcast?.statusDetail ?: "Conectando ao radar meteorológico da USP...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1),
                        lineHeight = 18.sp
                    )
                }
            }

            if (isOutdated) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF78350F).copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Aviso radar desatualizado",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Servidor IAG-USP transmitindo captura de ${nowcast?.fullScanDateTimeText} (${nowcast?.dataAgeDescription}). Alertas de chuva em tempo real estão temporariamente pausados.",
                            color = Color(0xFFFEF3C7),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Nowcasting Timeline Projection: +0m, +15m, +30m, +45m, +60m
            if (nowcast != null && nowcast.timeline.isNotEmpty()) {
                Text(
                    text = if (isOutdated) "Projeção da última imagem (${nowcast.lastScanTimeText}):"
                           else "Estimativa de Nowcasting (próximos 60 min):",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOutdated) Color(0xFFFDE68A) else Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    nowcast.timeline.forEach { step ->
                        TimelineStepItem(
                            step = step,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Stats Footer: Wind/motion vector & City coverage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF070F1E).copy(alpha = 0.7f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Motion Vector
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = "Deslocamento",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "${nowcast?.speedKmH?.roundToInt() ?: 0} km/h • ${nowcast?.directionLabel ?: "--"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Deslocamento das nuvens",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // City-wide rain coverage
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = "Cobertura SP",
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${cityCoverage?.totalRainPercent?.toInt() ?: 0}% da Grande SP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Sob chuva no momento",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineStepItem(
    step: TimelineProjection,
    modifier: Modifier = Modifier
) {
    val isRain = step.intensity.isRaining
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "+${step.minutesAhead}m",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isRain) step.intensity.color else Color(0xFF94A3B8)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(height = 26.dp, width = 36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isRain) step.intensity.color.copy(alpha = 0.25f)
                    else Color(0xFF1E293B)
                )
                .border(
                    width = 1.dp,
                    color = if (isRain) step.intensity.color else Color.Transparent,
                    shape = RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isRain) "${step.probabilityPercent}%" else "--",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRain) step.intensity.color else Color(0xFF64748B)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = step.timeLabel,
            fontSize = 9.sp,
            color = Color(0xFF64748B)
        )
    }
}
