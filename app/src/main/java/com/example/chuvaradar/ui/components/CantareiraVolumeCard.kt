package com.example.chuvaradar.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Water
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.chuvaradar.data.CantareiraDailyPoint
import com.example.chuvaradar.data.CantareiraOperationalBand
import com.example.chuvaradar.data.CantareiraUiState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun CantareiraVolumeCard(
    state: CantareiraUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val band = state.operationalBand
    val accentColor by animateColorAsState(
        targetValue = band?.color ?: Color(0xFF38BDF8),
        animationSpec = tween(400),
        label = "cantareiraAccent"
    )

    var selectedIndex by remember(state.points) { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cantareira_volume_card"),
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
                            Color(0xFF0284C7).copy(alpha = 0.16f),
                            Color(0xFF0B192C)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            // 1. Header
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
                            .background(Color(0xFF0284C7).copy(alpha = 0.25f))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Water,
                            contentDescription = stringResource(R.string.cantareira_title),
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.cantareira_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.cantareira_subtitle),
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (band != null) {
                        OperationalBandChip(band = band)
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    IconButton(
                        onClick = onRefresh,
                        enabled = !state.isRefreshing && !state.isLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("cantareira_refresh_button")
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF38BDF8)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.cantareira_refresh_desc),
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when {
                state.isLoading && state.points.isEmpty() -> {
                    CantareiraLoadingSection()
                }
                state.points.isEmpty() && state.errorMessage != null -> {
                    CantareiraErrorSection(
                        message = state.errorMessage,
                        onRetry = onRefresh
                    )
                }
                state.latest != null -> {
                    val latest = state.latest

                    // Aviso discreto caso esteja exibindo cache salvo offline
                    if (state.errorMessage != null && state.isFromCache) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF451A03).copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = state.errorMessage,
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }

                    // 2. Hero % Indicator + Deltas
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.cantareira_volume_label),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8)
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = formatPtBrNumber(latest.volumePercent, 1),
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = (-1).sp,
                                    modifier = Modifier.testTag("cantareira_volume_percent")
                                )
                                Text(
                                    text = "%",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                                )
                            }
                            Text(
                                text = "Atualizado em ${latest.fullDateLabel}${if (state.isFromCache) " • cache" else ""}",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            latest.dailyVariationPercent?.let { dailyVar ->
                                VariationBadge(
                                    delta = dailyVar,
                                    suffix = "dia"
                                )
                            }
                            state.delta30DaysPercent?.let { delta30 ->
                                VariationBadge(
                                    delta = delta30,
                                    suffix = "30d"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Reservoir Fill Gauge with official ANA/DAEE operational band markers
                    ReservoirGaugeBar(
                        volumePercent = latest.volumePercent,
                        accentColor = accentColor
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Interactive 30-day Chart (Volume % + Daily Rain mm)
                    CantareiraHistoryChartCard(
                        points = state.points,
                        selectedIndex = selectedIndex,
                        onSelectIndex = { selectedIndex = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. Hydrological Stats Strip
                    CantareiraStatsRow(
                        latest = latest,
                        minVolume = state.minVolume30Days,
                        maxVolume = state.maxVolume30Days
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6. Official SABESP Source Link
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
                    .testTag("cantareira_source_link")
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
                            text = stringResource(R.string.cantareira_source_title),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFCBD5E1)
                        )
                        Text(
                            text = stringResource(R.string.cantareira_source_url_label),
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.cantareira_open_portal_desc),
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationalBandChip(band: CantareiraOperationalBand) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = band.color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, band.color.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(band.color)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = band.shortLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = band.color
            )
        }
    }
}

@Composable
private fun VariationBadge(delta: Double, suffix: String) {
    val isPositive = delta > 0.04
    val isNegative = delta < -0.04
    val color = when {
        isPositive -> Color(0xFF10B981)
        isNegative -> Color(0xFFF97316)
        else -> Color(0xFF94A3B8)
    }
    val icon = when {
        isPositive -> Icons.AutoMirrored.Filled.TrendingUp
        isNegative -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    val sign = if (isPositive) "+" else ""

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${sign}${formatPtBrNumber(delta, 1)}% ($suffix)",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun ReservoirGaugeBar(
    volumePercent: Double,
    accentColor: Color
) {
    val targetFraction = (volumePercent.toFloat() / 100f).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "reservoirFill"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF070F1E))
                .border(1.dp, Color(0xFF1E3A63), RoundedCornerShape(8.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val fillWidth = size.width * animatedFraction
                if (fillWidth > 0f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF0284C7),
                                Color(0xFF38BDF8),
                                accentColor
                            ),
                            startX = 0f,
                            endX = fillWidth.coerceAtLeast(1f)
                        ),
                        size = Size(fillWidth, size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }

                // Marcadores das faixas oficiais ANA/DAEE (20%, 30%, 40%, 60%)
                val thresholds = listOf(0.20f, 0.30f, 0.40f, 0.60f)
                thresholds.forEach { t ->
                    val x = size.width * t
                    drawLine(
                        color = Color(0x99F8FAFC),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0%", fontSize = 9.5.sp, color = Color(0xFF64748B))
            Text("20%", fontSize = 9.5.sp, color = Color(0xFF64748B))
            Text("40%", fontSize = 9.5.sp, color = Color(0xFF64748B))
            Text("60%", fontSize = 9.5.sp, color = Color(0xFF64748B))
            Text("100%", fontSize = 9.5.sp, color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun CantareiraHistoryChartCard(
    points: List<CantareiraDailyPoint>,
    selectedIndex: Int?,
    onSelectIndex: (Int?) -> Unit
) {
    val activePoint = selectedIndex?.let { points.getOrNull(it) }
    val hasRainData = remember(points) { points.any { (it.rainMm ?: 0.0) > 0.0 } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070F1E))
            .border(1.dp, Color(0xFF1E3A63), RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("cantareira_chart")
    ) {
        // Chart header / dynamic scrub readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (activePoint != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${activePoint.fullDateLabel} • ${formatPtBrNumber(activePoint.volumePercent, 1)}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    val rainPart = activePoint.rainMm?.let { "Chuva: ${formatPtBrNumber(it, 1)} mm" }
                    val varPart = activePoint.dailyVariationPercent?.let {
                        val sign = if (it > 0.005) "+" else ""
                        "Var: $sign${formatPtBrNumber(it, 1)}%"
                    }
                    val detailText = listOfNotNull(rainPart, varPart).joinToString(" • ")
                    if (detailText.isNotEmpty()) {
                        Text(
                            text = detailText,
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectIndex(null) }
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
                    Text(
                        text = stringResource(R.string.cantareira_chart_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.cantareira_chart_hint),
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChartLegendDot(color = Color(0xFF38BDF8), label = "Vol. %")
                    if (hasRainData) {
                        ChartLegendDot(color = Color(0xFF22D3EE).copy(alpha = 0.7f), label = "Chuva")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (points.size >= 2) {
            CantareiraInteractiveCanvasChart(
                points = points,
                selectedIndex = selectedIndex,
                hasRainData = hasRainData,
                onSelectIndex = onSelectIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aguardando histórico diário suficiente para traçar o gráfico.",
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}

@Composable
private fun CantareiraInteractiveCanvasChart(
    points: List<CantareiraDailyPoint>,
    selectedIndex: Int?,
    hasRainData: Boolean,
    onSelectIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = remember {
        TextStyle(
            color = Color(0xFF64748B),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium
        )
    }

    // Calcula limites amigáveis do eixo Y (auto-escala suave para destacar a tendência mensal)
    val (yMin, yMax) = remember(points) {
        val rawMin = points.minOf { it.volumePercent }
        val rawMax = points.maxOf { it.volumePercent }
        val spread = (rawMax - rawMin).coerceAtLeast(1.0)
        val pad = (spread * 0.25).coerceIn(1.0, 5.0)
        val minBound = floor((rawMin - pad).coerceAtLeast(0.0))
        val maxBound = ceil((rawMax + pad).coerceAtMost(100.0))
        if (maxBound - minBound < 3.0) {
            (minBound - 1.5).coerceAtLeast(0.0) to (maxBound + 1.5).coerceAtMost(100.0)
        } else {
            minBound to maxBound
        }
    }

    val maxRainMm = remember(points) {
        (points.maxOfOrNull { it.rainMm ?: 0.0 } ?: 0.0).coerceAtLeast(5.0)
    }

    val leftPadDp = 38.dp
    val rightPadDp = 10.dp
    val topPadDp = 10.dp
    val bottomPadDp = 24.dp

    Canvas(
        modifier = modifier
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val leftPx = leftPadDp.toPx()
                    val rightPx = rightPadDp.toPx()
                    val plotW = (size.width - leftPx - rightPx).coerceAtLeast(1f)
                    val fraction = ((offset.x - leftPx) / plotW).coerceIn(0f, 1f)
                    val idx = (fraction * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
                    onSelectIndex(idx)
                }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures { change, _ ->
                    val leftPx = leftPadDp.toPx()
                    val rightPx = rightPadDp.toPx()
                    val plotW = (size.width - leftPx - rightPx).coerceAtLeast(1f)
                    val fraction = ((change.position.x - leftPx) / plotW).coerceIn(0f, 1f)
                    val idx = (fraction * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
                    onSelectIndex(idx)
                }
            }
    ) {
        val leftPad = leftPadDp.toPx()
        val rightPad = rightPadDp.toPx()
        val topPad = topPadDp.toPx()
        val bottomPad = bottomPadDp.toPx()

        val plotLeft = leftPad
        val plotRight = (size.width - rightPad).coerceAtLeast(plotLeft + 1f)
        val plotTop = topPad
        val plotBottom = (size.height - bottomPad).coerceAtLeast(plotTop + 1f)
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        val yRange = (yMax - yMin).coerceAtLeast(0.1)
        val gridSteps = 3
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

        // 1. Linhas de grade horizontais + rótulos do eixo Y (%)
        for (step in 0..gridSteps) {
            val ratio = step.toFloat() / gridSteps.toFloat()
            val y = plotBottom - ratio * plotHeight
            val valAtStep = yMin + ratio * yRange

            drawLine(
                color = if (step == 0) Color(0xFF1E3A63) else Color(0xFF1E3A63).copy(alpha = 0.55f),
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (step == 0) null else dashEffect
            )

            val label = "${formatPtBrNumber(valAtStep, if (yRange < 8.0) 1 else 0)}%"
            val measured = textMeasurer.measure(label, axisLabelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = (plotLeft - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                    y = (y - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height)
                )
            )
        }

        // 2. Barras sutis de Chuva Diária (mm - parâmetro 1) na base do gráfico
        if (hasRainData) {
            val barWidth = (plotWidth / points.size.toFloat() * 0.52f).coerceIn(3.dp.toPx(), 10.dp.toPx())
            val maxBarHeight = plotHeight * 0.38f

            points.forEachIndexed { idx, p ->
                val rain = p.rainMm ?: 0.0
                if (rain > 0.0) {
                    val xCenter = plotLeft + (idx.toFloat() / (points.size - 1).toFloat()) * plotWidth
                    val barH = ((rain / maxRainMm).toFloat().coerceIn(0f, 1f) * maxBarHeight)
                        .coerceAtLeast(2.dp.toPx())
                    val isSelected = selectedIndex == idx
                    drawRoundRect(
                        color = if (isSelected) Color(0xFF22D3EE).copy(alpha = 0.85f)
                        else Color(0xFF0EA5E9).copy(alpha = 0.36f),
                        topLeft = Offset(xCenter - barWidth / 2f, plotBottom - barH),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }
        }

        // 3. Coordenadas da curva de Volume Útil (%)
        val coords = points.mapIndexed { idx, p ->
            val x = plotLeft + (idx.toFloat() / (points.size - 1).toFloat()) * plotWidth
            val normY = ((p.volumePercent - yMin) / yRange).toFloat().coerceIn(0f, 1f)
            val y = plotBottom - normY * plotHeight
            Offset(x, y)
        }

        // Constrói caminho suave (curva cúbica de Bézier horizontal)
        val linePath = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 0 until coords.size - 1) {
                val p0 = coords[i]
                val p1 = coords[i + 1]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
        }

        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(coords.last().x, plotBottom)
            lineTo(coords.first().x, plotBottom)
            close()
        }

        // Preenchimento em gradiente sob a linha
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.30f),
                    Color(0xFF0284C7).copy(alpha = 0.08f),
                    Color.Transparent
                ),
                startY = plotTop,
                endY = plotBottom
            )
        )

        // Linha principal de Volume %
        drawPath(
            path = linePath,
            color = Color(0xFF38BDF8),
            style = Stroke(
                width = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 4. Ponto destacado (selecionado pelo usuário ou último dia)
        val focusIndex = selectedIndex ?: (coords.size - 1)
        val focusOffset = coords[focusIndex]

        if (selectedIndex != null) {
            drawLine(
                color = Color(0xFF38BDF8).copy(alpha = 0.55f),
                start = Offset(focusOffset.x, plotTop),
                end = Offset(focusOffset.x, plotBottom),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = dashEffect
            )
        }

        // Halo externo + ponto interno
        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = 0.28f),
            radius = 9.dp.toPx(),
            center = focusOffset
        )
        drawCircle(
            color = Color(0xFF070F1E),
            radius = 5.dp.toPx(),
            center = focusOffset
        )
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = 3.5.dp.toPx(),
            center = focusOffset
        )

        // 5. Rótulos do eixo X (datas dd/MM)
        val xLabelIndices = buildSet {
            add(0)
            if (points.size >= 6) {
                add(points.size / 3)
                add((points.size * 2) / 3)
            }
            add(points.size - 1)
        }.sorted()

        xLabelIndices.forEach { idx ->
            val label = points[idx].shortDateLabel
            val measured = textMeasurer.measure(label, axisLabelStyle)
            val centerX = coords[idx].x
            val clampedX = when (idx) {
                0 -> plotLeft
                points.size - 1 -> (plotRight - measured.size.width).coerceAtLeast(plotLeft)
                else -> (centerX - measured.size.width / 2f).coerceIn(plotLeft, plotRight - measured.size.width)
            }
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = clampedX,
                    y = plotBottom + 6.dp.toPx()
                )
            )
        }
    }
}

@Composable
private fun CantareiraStatsRow(
    latest: CantareiraDailyPoint,
    minVolume: Double?,
    maxVolume: Double?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tile 1: Chuva do dia (mm)
        val rainTodayText = latest.rainMm?.let { "${formatPtBrNumber(it, 1)} mm" } ?: "--"
        CantareiraStatTile(
            title = "Chuva no dia",
            value = rainTodayText,
            accentColor = Color(0xFF22D3EE),
            modifier = Modifier.weight(1f)
        )

        // Tile 2: Chuva no mês (ou Volume em hm³)
        val midTitle: String
        val midValue: String
        if (latest.monthlyRainMm != null) {
            midTitle = if (latest.historicalAvgRainMm != null) "Mês / Média" else "Chuva no mês"
            midValue = if (latest.historicalAvgRainMm != null) {
                "${formatPtBrNumber(latest.monthlyRainMm, 0)} / ${formatPtBrNumber(latest.historicalAvgRainMm, 0)}mm"
            } else {
                "${formatPtBrNumber(latest.monthlyRainMm, 1)} mm"
            }
        } else if (latest.volumeHm3 != null) {
            midTitle = "Volume útil"
            midValue = "${formatPtBrNumber(latest.volumeHm3, 0)} hm³"
        } else {
            midTitle = "Período"
            midValue = "30 dias"
        }
        CantareiraStatTile(
            title = midTitle,
            value = midValue,
            accentColor = Color(0xFF38BDF8),
            modifier = Modifier.weight(1f)
        )

        // Tile 3: Mín / Máx (30 dias)
        val rangeText = if (minVolume != null && maxVolume != null) {
            "${formatPtBrNumber(minVolume, 1)}–${formatPtBrNumber(maxVolume, 1)}%"
        } else {
            "--"
        }
        CantareiraStatTile(
            title = "Mín–Máx (30d)",
            value = rangeText,
            accentColor = Color(0xFF94A3B8),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CantareiraStatTile(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF070F1E).copy(alpha = 0.72f))
            .border(1.dp, Color(0xFF1E3A63).copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.WaterDrop,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = title,
                fontSize = 9.5.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CantareiraLoadingSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070F1E))
            .border(1.dp, Color(0xFF1E3A63), RoundedCornerShape(16.dp))
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp,
            color = Color(0xFF38BDF8)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.cantareira_loading),
            fontSize = 12.sp,
            color = Color(0xFFCBD5E1),
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Consultando série diária (30 dias) em mananciais.sabesp.com.br",
            fontSize = 10.5.sp,
            color = Color(0xFF64748B)
        )
    }
}

@Composable
private fun CantareiraErrorSection(
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
                containerColor = Color(0xFF0284C7),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("cantareira_retry_button")
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

private fun formatPtBrNumber(value: Double, decimals: Int): String {
    val safeVal = if (abs(value) < 0.0001) 0.0 else value
    return String.format(Locale("pt", "BR"), "%.${decimals}f", safeVal)
}
