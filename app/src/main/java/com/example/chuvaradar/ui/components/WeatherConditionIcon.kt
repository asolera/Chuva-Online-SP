package com.example.chuvaradar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.chuvaradar.data.WeatherVisualType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ícones meteorológicos nítidos desenhados em Canvas no estilo clássico de previsão
 * (sol, parcialmente nublado, nublado, garoa, chuva, chuva forte, nevoeiro e tempestade).
 */
@Composable
fun WeatherConditionIcon(
    visualType: WeatherVisualType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (visualType) {
            WeatherVisualType.CLEAR -> drawSun(center = Offset(size.width * 0.5f, size.height * 0.5f), radius = size.minDimension * 0.28f)
            WeatherVisualType.PARTLY_CLOUDY -> {
                drawSun(center = Offset(size.width * 0.64f, size.height * 0.36f), radius = size.minDimension * 0.19f)
                drawCloud(offsetY = size.height * 0.10f, scale = 0.88f, darkCloud = false)
            }
            WeatherVisualType.CLOUDY -> {
                drawCloud(offsetY = size.height * 0.06f, scale = 0.96f, darkCloud = false)
            }
            WeatherVisualType.FOG -> {
                drawCloud(offsetY = -size.height * 0.04f, scale = 0.88f, darkCloud = false)
                drawFogLines()
            }
            WeatherVisualType.DRIZZLE -> {
                drawCloud(offsetY = -size.height * 0.06f, scale = 0.88f, darkCloud = false)
                drawRainDrop(cx = size.width * 0.50f, topY = size.height * 0.66f, dropH = size.height * 0.22f)
            }
            WeatherVisualType.RAIN -> {
                drawCloud(offsetY = -size.height * 0.08f, scale = 0.90f, darkCloud = false)
                drawRainDrop(cx = size.width * 0.52f, topY = size.height * 0.64f, dropH = size.height * 0.26f)
            }
            WeatherVisualType.HEAVY_RAIN -> {
                drawCloud(offsetY = -size.height * 0.08f, scale = 0.90f, darkCloud = true)
                drawRainDrop(cx = size.width * 0.37f, topY = size.height * 0.65f, dropH = size.height * 0.23f)
                drawRainDrop(cx = size.width * 0.63f, topY = size.height * 0.65f, dropH = size.height * 0.23f)
            }
            WeatherVisualType.THUNDERSTORM -> {
                drawCloud(offsetY = -size.height * 0.10f, scale = 0.92f, darkCloud = true)
                drawLightningBolt(
                    cx = size.width * 0.42f,
                    topY = size.height * 0.52f,
                    boltH = size.height * 0.34f
                )
                drawLightningBolt(
                    cx = size.width * 0.62f,
                    topY = size.height * 0.48f,
                    boltH = size.height * 0.40f
                )
            }
        }
    }
}

private fun DrawScope.drawSun(center: Offset, radius: Float) {
    val rayCount = 8
    val innerR = radius * 1.25f
    val outerR = radius * 1.62f
    for (i in 0 until rayCount) {
        val angle = (i * 2.0 * PI / rayCount)
        val start = Offset(
            x = center.x + (cos(angle) * innerR).toFloat(),
            y = center.y + (sin(angle) * innerR).toFloat()
        )
        val end = Offset(
            x = center.x + (cos(angle) * outerR).toFloat(),
            y = center.y + (sin(angle) * outerR).toFloat()
        )
        drawLine(
            color = Color(0xFFFBBF24),
            start = start,
            end = end,
            strokeWidth = (size.minDimension * 0.06f).coerceAtLeast(1.6f),
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFDE047), Color(0xFFF59E0B)),
            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
            radius = radius * 1.4f
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawCloud(offsetY: Float, scale: Float, darkCloud: Boolean) {
    val w = size.width * scale
    val h = size.height * scale
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f + offsetY

    val brush = if (darkCloud) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFCBD5E1), Color(0xFF64748B)),
            startY = top + h * 0.15f,
            endY = top + h * 0.78f
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFF1F5F9), Color(0xFF94A3B8)),
            startY = top + h * 0.15f,
            endY = top + h * 0.78f
        )
    }

    // Puff esquerdo
    drawCircle(
        brush = brush,
        radius = w * 0.22f,
        center = Offset(left + w * 0.30f, top + h * 0.56f)
    )
    // Puff principal central-direito
    drawCircle(
        brush = brush,
        radius = w * 0.28f,
        center = Offset(left + w * 0.57f, top + h * 0.45f)
    )
    // Base arredondada da nuvem
    drawRoundRect(
        brush = brush,
        topLeft = Offset(left + w * 0.12f, top + h * 0.48f),
        size = Size(w * 0.74f, h * 0.28f),
        cornerRadius = CornerRadius(h * 0.14f, h * 0.14f)
    )
}

private fun DrawScope.drawRainDrop(cx: Float, topY: Float, dropH: Float) {
    val halfW = dropH * 0.36f
    val path = Path().apply {
        moveTo(cx, topY)
        cubicTo(
            cx + halfW * 1.15f, topY + dropH * 0.52f,
            cx + halfW, topY + dropH,
            cx, topY + dropH
        )
        cubicTo(
            cx - halfW, topY + dropH,
            cx - halfW * 1.15f, topY + dropH * 0.52f,
            cx, topY
        )
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF60A5FA), Color(0xFF2563EB)),
            startY = topY,
            endY = topY + dropH
        )
    )
}

private fun DrawScope.drawLightningBolt(cx: Float, topY: Float, boltH: Float) {
    val w = boltH * 0.54f
    val path = Path().apply {
        moveTo(cx + w * 0.10f, topY)
        lineTo(cx - w * 0.42f, topY + boltH * 0.54f)
        lineTo(cx - w * 0.02f, topY + boltH * 0.54f)
        lineTo(cx - w * 0.22f, topY + boltH)
        lineTo(cx + w * 0.46f, topY + boltH * 0.38f)
        lineTo(cx + w * 0.06f, topY + boltH * 0.38f)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFACC15), Color(0xFFEA580C)),
            startY = topY,
            endY = topY + boltH
        )
    )
}

private fun DrawScope.drawFogLines() {
    val strokeW = (size.height * 0.06f).coerceAtLeast(1.8f)
    val y1 = size.height * 0.72f
    val y2 = size.height * 0.84f
    drawLine(
        color = Color(0xFF94A3B8),
        start = Offset(size.width * 0.18f, y1),
        end = Offset(size.width * 0.82f, y1),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFF64748B),
        start = Offset(size.width * 0.26f, y2),
        end = Offset(size.width * 0.74f, y2),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
}
