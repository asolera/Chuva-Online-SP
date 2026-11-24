package com.example.chuvaradar.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chuvaradar.data.OpenStreetMapProvider
import com.example.chuvaradar.data.RadarScanFrame
import com.example.chuvaradar.data.RainIntensity
import com.example.chuvaradar.data.UserSavedAddress
import com.example.chuvaradar.engine.RadarAnalysisEngine

@Composable
fun RadarViewer(
    frames: List<RadarScanFrame>,
    activeFrameIndex: Int,
    isPlaying: Boolean,
    userAddress: UserSavedAddress?,
    showUserPin: Boolean,
    showRangeRings: Boolean,
    showDistrictLabels: Boolean,
    onPlayPauseToggle: () -> Unit,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    onSeekFrame: (Int) -> Unit,
    onTogglePin: () -> Unit,
    onToggleRings: () -> Unit,
    onToggleDistricts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var osmStyle by remember { mutableIntStateOf(1) } // 1: OSM Padrão (Sem marcas d'água), 0: OSM Dark limpo

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val osmBasemap = remember(osmStyle) {
        if (osmStyle == 0) OpenStreetMapProvider.getDarkBasemap(context)
        else OpenStreetMapProvider.getStandardBasemap(context)
    }

    val activeFrame = frames.getOrNull(activeFrameIndex)
    val totalFrames = frames.size
    val isFrameOutdated = activeFrame?.isOutdated == true

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("radar_viewer_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF070F1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Live animation badge + frame counter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isFrameOutdated -> Color(0xFFF59E0B)
                                    isPlaying -> Color(0xFF10B981)
                                    else -> Color(0xFFF59E0B)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isFrameOutdated -> "Radar (Desatualizado)"
                            isPlaying -> "Radar Ao Vivo (Loop)"
                            else -> "Radar Pausado"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isFrameOutdated) Color(0xFF451A03).copy(alpha = 0.85f) else Color(0xFF1E293B),
                    border = if (isFrameOutdated) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)) else null
                ) {
                    Text(
                        text = "Frame ${activeFrameIndex + 1}/$totalFrames • ${activeFrame?.timestampText ?: "--:--"}${if (isFrameOutdated) " ⚠️" else ""}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isFrameOutdated) Color(0xFFFDE68A) else Color(0xFF38BDF8),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Radar Visualizer Box (1:1 Aspect Ratio) com suporte a Zoom e Pan
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF040A14))
                    .border(1.dp, Color(0xFF1E3A63), RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                            zoomScale = newScale
                            panOffset = if (newScale == 1f) Offset.Zero else {
                                val maxPan = 450f * (newScale - 1f)
                                Offset(
                                    (panOffset.x + pan.x).coerceIn(-maxPan, maxPan),
                                    (panOffset.y + pan.y).coerceIn(-maxPan, maxPan)
                                )
                            }
                        }
                    }
            ) {
                // Conteúdo do Mapa e Radar com transformação de Zoom e Pan
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                ) {
                    // 1. CAMADA BASE: Mapa OpenStreetMap Real (Sem marca d'água)
                    if (osmBasemap != null) {
                        Image(
                            bitmap = osmBasemap.asImageBitmap(),
                            contentDescription = "Mapa OpenStreetMap de São Paulo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    }

                    // 2. CAMADA DO RADAR: Ecos de Chuva IAG-USP (com fundo transparente)
                    if (activeFrame?.bitmap != null) {
                        Image(
                            bitmap = activeFrame.bitmap.asImageBitmap(),
                            contentDescription = "Ecos de chuva do radar IAG-USP",
                            alpha = 0.92f,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "Sincronizando fotos do radar IAG-USP...",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // 3. CAMADA DE SOBREPOSIÇÃO: Anéis de alcance e pino do usuário
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val scaleX = w / 1000f
                        val scaleY = h / 1000f
                        val density = this.density

                        val radarCenter = Offset(505f * scaleX, 495f * scaleY)

                        // Anéis de alcance (10 km, 20 km, 30 km, 36 km)
                        if (showRangeRings) {
                            val ringDistancesKm = listOf(10f, 20f, 30f, 36f)
                            ringDistancesKm.forEach { dKm ->
                                val rPx = (dKm / RadarAnalysisEngine.KM_PER_PIXEL.toFloat()) * scaleX
                                drawCircle(
                                    color = Color(0x6638BDF8),
                                    radius = rPx,
                                    center = radarCenter,
                                    style = Stroke(width = 1.2f * density)
                                )
                            }
                        }

                        // Pino do Usuário com efeito pulsante
                        if (showUserPin && userAddress != null) {
                            val userPx = RadarAnalysisEngine.geoToPixel(userAddress.latitude, userAddress.longitude)
                            if (userPx != null) {
                                val userOffset = Offset(userPx.first * scaleX, userPx.second * scaleY)

                                // Onda de radar pulsante
                                drawCircle(
                                    color = Color(0xFF38BDF8).copy(alpha = pulseAlpha),
                                    radius = pulseRadius * scaleX,
                                    center = userOffset,
                                    style = Stroke(width = 2.5f * density)
                                )

                                // Ponto central
                                drawCircle(
                                    color = Color(0xFF0284C7),
                                    radius = 7f,
                                    center = userOffset
                                )
                                drawCircle(
                                    color = Color(0xFF38BDF8),
                                    radius = 4f,
                                    center = userOffset
                                )

                                // Mira em cruz
                                drawLine(
                                    color = Color(0xFF38BDF8),
                                    start = Offset(userOffset.x - 14f, userOffset.y),
                                    end = Offset(userOffset.x + 14f, userOffset.y),
                                    strokeWidth = 1.8f * density
                                )
                                drawLine(
                                    color = Color(0xFF38BDF8),
                                    start = Offset(userOffset.x, userOffset.y - 14f),
                                    end = Offset(userOffset.x, userOffset.y + 14f),
                                    strokeWidth = 1.8f * density
                                )
                            }
                        }
                    }
                }

                // Selo Flutuante de dados desatualizados no canto do mapa
                if (isFrameOutdated && activeFrame != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xEE451A03),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Captura: ${activeFrame.fullDateTimeText.ifBlank { activeFrame.timestampText }} (${activeFrame.dataAgeDescription})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFEF3C7)
                            )
                        }
                    }
                }

                // Botões Flutuantes de Zoom (+, −, Reset)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable {
                                zoomScale = (zoomScale + 0.5f).coerceAtMost(4.0f)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.75f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable {
                                val nextScale = (zoomScale - 0.5f).coerceAtLeast(1.0f)
                                zoomScale = nextScale
                                if (nextScale == 1.0f) panOffset = Offset.Zero
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    if (zoomScale > 1.0f) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0284C7),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    zoomScale = 1.0f
                                    panOffset = Offset.Zero
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Reset Zoom", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Selo OpenStreetMap + Legenda de Precipitação no rodapé
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xEE070F1E))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (zoomScale > 1f) "${(zoomScale * 100).toInt()}% • OSM" else "© OpenStreetMap",
                        fontSize = 8.5.sp,
                        color = Color(0xFF94A3B8)
                    )
                    RadarScaleBar()
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playback Slider / Scrubber
            if (totalFrames >= 2) {
                val safeMax = (totalFrames - 1).toFloat()
                val safeValue = activeFrameIndex.toFloat().coerceIn(0f, safeMax)
                Slider(
                    value = safeValue,
                    onValueChange = { onSeekFrame(it.toInt().coerceIn(0, totalFrames - 1)) },
                    valueRange = 0f..safeMax,
                    steps = (totalFrames - 2).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF0284C7),
                        inactiveTrackColor = Color(0xFF1E293B)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("radar_timeline_slider")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra de Controles de Camadas e Ações
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = showUserPin,
                    onClick = onTogglePin,
                    label = { Text("Meu Local", fontSize = 11.5.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(13.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )

                FilterChip(
                    selected = showRangeRings,
                    onClick = onToggleRings,
                    label = { Text("Alcance", fontSize = 11.5.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )

                val isLatestFixed = !isPlaying && totalFrames > 0 && activeFrameIndex == totalFrames - 1
                FilterChip(
                    selected = isLatestFixed,
                    onClick = {
                        if (totalFrames > 0) {
                            if (isPlaying) {
                                onPlayPauseToggle()
                            }
                            onSeekFrame(totalFrames - 1)
                        }
                    },
                    label = { Text("Última imagem", fontSize = 11.5.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )

                FilterChip(
                    selected = osmStyle == 1,
                    onClick = { osmStyle = if (osmStyle == 1) 0 else 1 },
                    label = { Text(if (osmStyle == 1) "OSM Padrão" else "OSM Dark", fontSize = 11.5.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(13.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Barra de Reprodução Dedicada (Centralizada Abaixo, Ampla e Confortável)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onStepBackward,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Voltar frame",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier
                        .size(52.dp)
                        .clickable(onClick = onPlayPauseToggle)
                        .testTag("radar_play_pause_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar animação" else "Iniciar animação",
                            tint = Color(0xFF070F1E),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                IconButton(
                    onClick = onStepForward,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Avançar frame",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RadarScaleBar() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Precipitação:", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
        ScaleItem(color = RainIntensity.VERY_LIGHT.color, label = "<2")
        ScaleItem(color = RainIntensity.LIGHT.color, label = "5")
        ScaleItem(color = RainIntensity.MODERATE.color, label = "15")
        ScaleItem(color = RainIntensity.HEAVY.color, label = "40")
        ScaleItem(color = RainIntensity.INTENSE.color, label = ">40")
    }
}

@Composable
private fun ScaleItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 11.dp, height = 6.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(text = label, fontSize = 7.5.sp, color = Color(0xFFCBD5E1))
    }
}
