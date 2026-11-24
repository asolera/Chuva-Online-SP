package com.example.chuvaradar.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.chuvaradar.data.DailyForecastItem
import com.example.chuvaradar.data.HourlyRainPoint
import com.example.chuvaradar.data.OpenMeteoParser
import com.example.chuvaradar.data.WeatherForecastUiState
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Card de Previsão do Tempo inspirado no visual clássico de gráfico de chuva em degraus
 * (00:00..21:00) + faixa horizontal interativa dos próximos 8 dias, alimentado pelo Open-Meteo.
 */
@Composable
fun WeatherForecastCard(
    state: WeatherForecastUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val days = state.forecast?.days.orEmpty()

    var selectedDayIndex by remember(days.firstOrNull()?.dateIso) { mutableIntStateOf(0) }
    val safeDayIndex = selectedDayIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0))
    val selectedDay = days.getOrNull(safeDayIndex)

    var scrubbedHourIndex by remember(safeDayIndex, selectedDay?.dateIso) { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weather_forecast_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E36)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2563EB).copy(alpha = 0.14f),
                            Color(0xFF0B192C)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            // 1. Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.22f))
                            .border(1.dp, Color(0xFF60A5FA).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = stringResource(R.string.forecast_title),
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.forecast_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val forecast = state.forecast
                        val subtitle = if (forecast != null) {
                            val tempPart = forecast.currentTemperatureC?.let { "${it.roundToInt()}°" }
                            val condPart = forecast.currentCondition?.descriptionPt
                            listOfNotNull(forecast.locationLabel, tempPart, condPart).joinToString(" • ")
                        } else {
                            stringResource(R.string.forecast_subtitle)
                        }
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !state.isRefreshing && !state.isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("forecast_refresh_button")
                ) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF60A5FA)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.forecast_refresh_desc),
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                state.isLoading && selectedDay == null -> {
                    ForecastLoadingSection()
                }
                selectedDay == null && state.errorMessage != null -> {
                    ForecastErrorSection(
                        message = state.errorMessage,
                        onRetry = onRefresh
                    )
                }
                selectedDay != null -> {
                    // Aviso discreto se estiver exibindo cache offline
                    if (state.errorMessage != null && state.isFromCache) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF451A03).copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = state.errorMessage,
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // 2. Painel do gráfico de probabilidade de chuva em degraus (00:00..21:00)
                    HourlyRainStepChartCard(
                        day = selectedDay,
                        isToday = safeDayIndex == 0,
                        scrubbedHourIndex = scrubbedHourIndex,
                        onScrubHour = { scrubbedHourIndex = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Faixa horizontal dos próximos 8 dias (estilo seg., ter., qua. ...)
                    DailyForecastStrip(
                        days = days,
                        selectedIndex = safeDayIndex,
                        onSelectDay = { idx ->
                            selectedDayIndex = idx
                            scrubbedHourIndex = null
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Rodapé com crédito oficial Open-Meteo
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF070F1E).copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color(0xFF1E3A63)),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.sourceUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // Ignora se nenhum navegador estiver disponível
                        }
                    }
                    .testTag("forecast_source_link")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.forecast_source_title),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            text = stringResource(R.string.forecast_source_url_label),
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.forecast_open_source_desc),
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlyRainStepChartCard(
    day: DailyForecastItem,
    isToday: Boolean,
    scrubbedHourIndex: Int?,
    onScrubHour: (Int?) -> Unit
) {
    val hourly = remember(day) {
        // Garante exatamente 24 slots (00h..23h) para o gráfico em degraus
        val mapByHour = day.hourlyPoints.associateBy { it.hourOfDay }
        (0..23).map { h ->
            mapByHour[h] ?: HourlyRainPoint(
                isoTime = "${day.dateIso}T${String.format(Locale.US, "%02d:00", h)}",
                hourOfDay = h,
                hourLabel = String.format(Locale.US, "%02d:00", h),
                precipitationProbability = 0,
                precipitationMm = 0.0,
                temperatureC = day.minTempC,
                weatherCode = day.weatherCode
            )
        }
    }

    // 8 blocos de 3 horas (00:00, 03:00, ..., 21:00) com a probabilidade pico de cada faixa
    val bucketLabels = remember(hourly) {
        (0 until 8).map { bucket ->
            val startHour = bucket * 3
            val bucketPeak = (startHour until (startHour + 3)).maxOf { h ->
                hourly[h].precipitationProbability
            }
            val timeLabel = String.format(Locale.US, "%02d:00", startHour)
            timeLabel to bucketPeak
        }
    }

    val activeHour = scrubbedHourIndex?.let { hourly.getOrNull(it) }
    val currentSpHour = remember(isToday) { if (isToday) OpenMeteoParser.currentSaoPauloHour() else -1 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070F1E))
            .border(1.dp, Color(0xFF1E3A63), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag("forecast_rain_step_chart")
    ) {
        // Faixa superior de título / detalhe ao tocar no gráfico
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activeHour != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${day.weekdayShort} (${day.fullDateLabel}) • ${activeHour.hourLabel}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF60A5FA)
                    )
                    val mmStr = String.format(Locale("pt", "BR"), "%.1f", activeHour.precipitationMm)
                    Text(
                        text = "Chuva: ${activeHour.precipitationProbability}% ($mmStr mm) • ${activeHour.temperatureC.roundToInt()}° • ${activeHour.condition.descriptionPt}",
                        fontSize = 10.5.sp,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onScrubHour(null) }
                ) {
                    Text(
                        text = "Limpar",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    val dayHeader = if (isToday) {
                        "Chuva hoje (${day.fullDateLabel})"
                    } else {
                        "Chuva em ${day.weekdayShort} (${day.fullDateLabel})"
                    }
                    Text(
                        text = dayHeader,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "${day.condition.descriptionPt} • máx. ${day.maxRainProbability}% • toque no gráfico",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Linha de percentuais no topo (45%, 10%, 0%, 10%, 40%, 45%, 15%, 20%)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            bucketLabels.forEach { (_, prob) ->
                Text(
                    text = "$prob%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (prob > 0) Color(0xFF7CB3FF) else Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Gráfico em degraus hora a hora (24 passos)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .pointerInput(hourly) {
                    detectTapGestures { offset ->
                        val stepW = size.width / 24f
                        val idx = (offset.x / stepW).toInt().coerceIn(0, 23)
                        onScrubHour(idx)
                    }
                }
                .pointerInput(hourly) {
                    detectHorizontalDragGestures { change, _ ->
                        val stepW = size.width / 24f
                        val idx = (change.position.x / stepW).toInt().coerceIn(0, 23)
                        onScrubHour(idx)
                    }
                }
        ) {
            val count = hourly.size
            val stepWidth = size.width / count.toFloat()
            val topPad = 4.dp.toPx()
            val bottomY = size.height - 2.dp.toPx()
            val usableHeight = (bottomY - topPad).coerceAtLeast(1f)

            // Escala dinâmica suave (mínimo 60% no teto para dias de chuva moderada terem boa leitura visual)
            val maxProbInDay = hourly.maxOf { it.precipitationProbability }
            val scaleMax = when {
                maxProbInDay <= 50 -> 60f
                maxProbInDay <= 80 -> 85f
                else -> 100f
            }

            // Linha base sutil
            drawLine(
                color = Color(0xFF1E3A63).copy(alpha = 0.7f),
                start = Offset(0f, bottomY),
                end = Offset(size.width, bottomY),
                strokeWidth = 1.dp.toPx()
            )

            // Caminho preenchido em degraus
            val fillPath = Path().apply {
                moveTo(0f, bottomY)
                hourly.forEachIndexed { idx, point ->
                    val x0 = idx * stepWidth
                    val x1 = (idx + 1) * stepWidth
                    val ratio = (point.precipitationProbability / scaleMax).coerceIn(0f, 1f)
                    val y = bottomY - (ratio * usableHeight)
                    lineTo(x0, y)
                    lineTo(x1, y)
                }
                lineTo(size.width, bottomY)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3B82F6).copy(alpha = 0.34f),
                        Color(0xFF1E3A5F).copy(alpha = 0.22f)
                    ),
                    startY = topPad,
                    endY = bottomY
                )
            )

            // Destaque do degrau selecionado (scrubbing)
            if (scrubbedHourIndex != null && scrubbedHourIndex in 0 until count) {
                val pt = hourly[scrubbedHourIndex]
                val x0 = scrubbedHourIndex * stepWidth
                val ratio = (pt.precipitationProbability / scaleMax).coerceIn(0f, 1f)
                val y = bottomY - (ratio * usableHeight)
                drawRect(
                    color = Color(0xFF60A5FA).copy(alpha = 0.30f),
                    topLeft = Offset(x0, y),
                    size = Size(stepWidth, bottomY - y)
                )
            }

            // Linha azul brilhante no topo de cada degrau (assinatura visual do gráfico)
            val capStroke = 2.4.dp.toPx()
            hourly.forEachIndexed { idx, point ->
                val x0 = idx * stepWidth
                val x1 = (idx + 1) * stepWidth
                val ratio = (point.precipitationProbability / scaleMax).coerceIn(0f, 1f)
                val y = bottomY - (ratio * usableHeight)
                val isSelected = scrubbedHourIndex == idx
                drawLine(
                    color = if (isSelected) Color(0xFF93C5FD) else Color(0xFF4F8DFD),
                    start = Offset(x0, y),
                    end = Offset(x1, y),
                    strokeWidth = if (isSelected) 3.2.dp.toPx() else capStroke,
                    cap = StrokeCap.Butt
                )
            }

            // Marcador sutil do horário atual ("agora") no dia de hoje
            if (currentSpHour in 0 until count) {
                val nowX = (currentSpHour + 0.5f) * stepWidth
                drawLine(
                    color = Color(0xFF38BDF8).copy(alpha = 0.55f),
                    start = Offset(nowX, topPad),
                    end = Offset(nowX, bottomY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Eixo X de horários (00:00, 03:00, ..., 21:00)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            bucketLabels.forEach { (timeLabel, _) ->
                Text(
                    text = timeLabel,
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DailyForecastStrip(
    days: List<DailyForecastItem>,
    selectedIndex: Int,
    onSelectDay: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .testTag("forecast_days_strip"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEachIndexed { index, day ->
            val isSelected = index == selectedIndex
            Column(
                modifier = Modifier
                    .width(66.dp)
                    .defaultMinSize(minHeight = 96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) Color(0xFF1A2D4A)
                        else Color(0xFF070F1E).copy(alpha = 0.55f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF60A5FA).copy(alpha = 0.65f)
                        else Color(0xFF1E3A63).copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectDay(index) }
                    .padding(horizontal = 6.dp, vertical = 10.dp)
                    .testTag("forecast_day_item_$index"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = day.weekdayShort,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(6.dp))

                WeatherConditionIcon(
                    visualType = day.condition.visualType,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${day.maxTempC.roundToInt()}°",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${day.minTempC.roundToInt()}°",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastLoadingSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070F1E))
            .border(1.dp, Color(0xFF1E3A63), RoundedCornerShape(16.dp))
            .padding(vertical = 26.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.5.dp,
            color = Color(0xFF60A5FA)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.forecast_loading),
            fontSize = 12.sp,
            color = Color(0xFFCBD5E1),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ForecastErrorSection(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070F1E))
            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 12.sp,
            color = Color(0xFFCBD5E1),
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("forecast_retry_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.cantareira_retry),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
