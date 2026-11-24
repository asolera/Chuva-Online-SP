package com.example.chuvaradar.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chuvaradar.data.DistrictData
import com.example.chuvaradar.data.DistrictRainStatus
import com.example.chuvaradar.data.OpenStreetMapProvider
import com.example.chuvaradar.data.RainIntensity
import com.example.chuvaradar.data.UserSavedAddress
import com.example.chuvaradar.engine.RadarAnalysisEngine

/**
 * Escala de cores do balão de distritos:
 * 0% = Verde puro (#16A34A)
 * 25% = Amarelo-Verde (#84CC16)
 * 50% = Amarelo (#EAB308)
 * 75% = Laranja (#EA580C)
 * 100% = Vermelho bem intenso (#991B1B)
 */
fun getDistrictRainPercentageColor(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return when {
        f <= 0.0f -> Color(0xFF16A34A)
        f < 0.25f -> lerp(Color(0xFF16A34A), Color(0xFF84CC16), f / 0.25f)
        f < 0.50f -> lerp(Color(0xFF84CC16), Color(0xFFEAB308), (f - 0.25f) / 0.25f)
        f < 0.75f -> lerp(Color(0xFFEAB308), Color(0xFFEA580C), (f - 0.50f) / 0.25f)
        else -> lerp(Color(0xFFEA580C), Color(0xFF991B1B), (f - 0.75f) / 0.25f)
    }
}

@Composable
fun DistrictMinimap(
    districts: List<DistrictRainStatus>,
    selectedDistrict: DistrictRainStatus?,
    userAddress: UserSavedAddress? = null,
    showUserPin: Boolean = false,
    showRangeRings: Boolean = false,
    onTogglePin: () -> Unit = {},
    onToggleRings: () -> Unit = {},
    onSelectDistrict: (DistrictRainStatus?) -> Unit,
    onSetAsMyAddress: (DistrictRainStatus) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var osmStyle by remember { mutableIntStateOf(1) } // 1: OSM Padrão (Sem marcas d'água), 0: OSM Dark limpo
    var forecastMode by remember { mutableIntStateOf(0) } // 0: Agora (Ao Vivo), 1: Previsão (+30 min), 2: Previsão (+60 min)

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val osmBasemap = remember(osmStyle) {
        if (osmStyle == 0) OpenStreetMapProvider.getDarkBasemap(context)
        else OpenStreetMapProvider.getStandardBasemap(context)
    }

    var selectedZone by remember { mutableStateOf("Todos") }

    val filteredDistricts = remember(districts, selectedZone) {
        if (selectedZone == "Todos") districts
        else districts.filter { it.zone == selectedZone }
    }

    val totalCount = districts.size

    // Contagem da situação Atual
    val currentRainingCount = remember(districts) {
        districts.count { it.rainPercent >= 8f && it.maxIntensity.isRaining }
    }
    val currentFraction = if (totalCount > 0) currentRainingCount.toFloat() / totalCount.toFloat() else 0f
    val currentPercent = (currentFraction * 100f).toInt()

    // Contagem da Previsão (+30 min)
    val forecast30mRainingCount = remember(districts) {
        districts.count { it.forecast30mRainPercent >= 8f && it.forecast30mIntensity.isRaining }
    }
    val forecast30mFraction = if (totalCount > 0) forecast30mRainingCount.toFloat() / totalCount.toFloat() else 0f
    val forecast30mPercent = (forecast30mFraction * 100f).toInt()

    // Contagem da Previsão (+60 min)
    val forecast60mRainingCount = remember(districts) {
        districts.count { it.forecast60mRainPercent >= 8f && it.forecast60mIntensity.isRaining }
    }
    val forecast60mFraction = if (totalCount > 0) forecast60mRainingCount.toFloat() / totalCount.toFloat() else 0f
    val forecast60mPercent = (forecast60mFraction * 100f).toInt()

    // Seleciona as métricas ativas de acordo com o modo
    val (activeRainingCount, activeFraction, activePercent) = when (forecastMode) {
        1 -> Triple(forecast30mRainingCount, forecast30mFraction, forecast30mPercent)
        2 -> Triple(forecast60mRainingCount, forecast60mFraction, forecast60mPercent)
        else -> Triple(currentRainingCount, currentFraction, currentPercent)
    }

    val targetBannerColor = getDistrictRainPercentageColor(activeFraction)
    val animatedBannerBg by animateColorAsState(
        targetValue = targetBannerColor,
        animationSpec = tween(500),
        label = "bannerColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "district_pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "districtPulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "districtPulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("district_minimap_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E36)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 1. Header do Minimapa
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Distritos SP",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = when (forecastMode) {
                            1 -> "Projeção de chuva estimada para daqui a 30 min"
                            2 -> "Projeção de chuva estimada para daqui a 60 min"
                            else -> "Monitoramento de chuva em tempo real"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Seletor de Modo Triplo: Agora (Ao Vivo) | Previsão (+30 min) | Previsão (+60 min)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = forecastMode == 0,
                    onClick = { forecastMode = 0 },
                    label = {
                        Text(
                            "Agora",
                            fontSize = 11.5.sp,
                            fontWeight = if (forecastMode == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(13.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = forecastMode == 1,
                    onClick = { forecastMode = 1 },
                    label = {
                        Text(
                            "+30 min",
                            fontSize = 11.5.sp,
                            fontWeight = if (forecastMode == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(13.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = forecastMode == 2,
                    onClick = { forecastMode = 2 },
                    label = {
                        Text(
                            "+60 min",
                            fontSize = 11.5.sp,
                            fontWeight = if (forecastMode == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(13.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Controles de Camadas: "Meu Local", "Alcance" e "OSM Padrão / Dark"
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
                    label = { Text("Meu Local", fontSize = 11.sp) },
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
                    label = { Text("Alcance", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.LocationSearching, contentDescription = null, modifier = Modifier.size(13.dp))
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
                    label = { Text(if (osmStyle == 1) "OSM Padrão" else "OSM Dark", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(12.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF0284C7),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFF94A3B8)
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Balão de largura total com escala de cor (0% verde -> 100% vermelho intenso)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = animatedBannerBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("district_rain_status_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (activeRainingCount > 0) Icons.Default.WaterDrop else Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = when (forecastMode) {
                                1 -> "$activeRainingCount/$totalCount distritos c/ chuva em +30m ($activePercent%)"
                                2 -> "$activeRainingCount/$totalCount distritos c/ chuva em +60m ($activePercent%)"
                                else -> "$activeRainingCount/$totalCount distritos c/ chuva ($activePercent%)"
                            },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (forecastMode) {
                                1 -> when {
                                    activePercent == 0 -> "Tempo seco projetado para daqui a 30 min"
                                    activePercent <= 20 -> "Chuva isolada projetada em poucos distritos"
                                    activePercent <= 45 -> "Atenção: aproximação de pancadas de chuva"
                                    activePercent <= 75 -> "Alerta: avanço de chuva para a capital"
                                    else -> "Alerta Máximo: temporais previstos na capital"
                                }
                                2 -> when {
                                    activePercent == 0 -> "Tempo seco projetado para daqui a 60 min"
                                    activePercent <= 20 -> "Chuva isolada projetada em poucos distritos"
                                    activePercent <= 45 -> "Atenção: aproximação de pancadas de chuva"
                                    activePercent <= 75 -> "Alerta: avanço de chuva ampla para a capital"
                                    else -> "Alerta Máximo: temporais previstos na capital"
                                }
                                else -> when {
                                    activePercent == 0 -> "Tempo seco em toda a capital"
                                    activePercent <= 20 -> "Chuva isolada em poucos distritos"
                                    activePercent <= 45 -> "Atenção: pancadas de chuva na cidade"
                                    activePercent <= 75 -> "Alerta: chuva generalizada na capital"
                                    else -> "Alerta Máximo: 100% da capital com chuva"
                                }
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.95f),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Zone Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DistrictData.zones.forEach { zone ->
                    FilterChip(
                        selected = selectedZone == zone,
                        onClick = { selectedZone = zone },
                        label = { Text(zone, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. MAPA DE DISTRITOS: OpenStreetMap + Shapefile dos Distritos com Zoom, Pan, Pino e Alcance
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.15f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF080F1D))
                    .border(1.dp, Color(0xFF1E3A63), RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 6.0f)
                            zoomScale = newScale
                            panOffset = if (newScale == 1f) Offset.Zero else {
                                val maxPan = 550f * (newScale - 1f)
                                Offset(
                                    (panOffset.x + pan.x).coerceIn(-maxPan, maxPan),
                                    (panOffset.y + pan.y).coerceIn(-maxPan, maxPan)
                                )
                            }
                        }
                    }
            ) {
                // Conteúdo transformável (OSM + Shapefile dos Distritos + Anéis de Alcance + Pino do Usuário)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                            transformOrigin = TransformOrigin.Center
                        }
                ) {
                    // A. Base OpenStreetMap
                    if (osmBasemap != null) {
                        Image(
                            bitmap = osmBasemap.asImageBitmap(),
                            contentDescription = "Mapa OpenStreetMap de fundo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds
                        )
                    }

                    // B. Camada Shapefile dos Distritos (Polígonos Reais de São Paulo) + Overlays
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(filteredDistricts) {
                                detectTapGestures { tapOffset ->
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()
                                    val scaleX = w / 1000f
                                    val scaleY = h / 1000f

                                    // Dentro do graphicsLayer, o Compose já entrega o tapOffset nas coordenadas locais exatas do Canvas (0..w, 0..h)
                                    val tapPxX = (tapOffset.x / scaleX).toInt().coerceIn(0, 999)
                                    val tapPxY = (tapOffset.y / scaleY).toInt().coerceIn(0, 999)

                                    val (tapLat, tapLon) = RadarAnalysisEngine.pixelToGeo(tapPxX, tapPxY)
                                    val hit = OpenStreetMapProvider.findDistrictAt(tapLat, tapLon, filteredDistricts)
                                    if (hit != null) {
                                        onSelectDistrict(hit)
                                    }
                                }
                            }
                    ) {
                        val w = size.width
                        val h = size.height
                        val scaleX = w / 1000f
                        val scaleY = h / 1000f
                        val density = this.density

                        // Fator de escala para afinar traços e marcadores conforme o zoom aumenta
                        val strokeScaleFactor = zoomScale * (1f + 0.22f * (zoomScale - 1f))
                        val markerScaleFactor = zoomScale * (1f + 0.18f * (zoomScale - 1f))

                        fun geoToOffset(lat: Double, lon: Double): Offset? {
                            val px = RadarAnalysisEngine.geoToPixel(lat, lon) ?: return null
                            return Offset(px.first * scaleX, px.second * scaleY)
                        }

                        // 1. Desenha TODOS os 96 distritos com contornos e preenchimento nítidos
                        filteredDistricts.forEach { dist ->
                            val isSelected = dist.id == selectedDistrict?.id

                            // Determina intensidade e porcentagem de chuva com base no modo selecionado
                            val (rainPct, intensity) = when (forecastMode) {
                                1 -> Pair(dist.forecast30mRainPercent, dist.forecast30mIntensity)
                                2 -> Pair(dist.forecast60mRainPercent, dist.forecast60mIntensity)
                                else -> Pair(dist.rainPercent, dist.maxIntensity)
                            }
                            val hasRain = rainPct >= 8f && intensity.isRaining

                            for (ring in dist.polygons) {
                                val path = Path()
                                var first = true
                                for ((lat, lon) in ring) {
                                    val pt = geoToOffset(lat, lon)
                                    if (pt != null) {
                                        if (first) {
                                            path.moveTo(pt.x, pt.y)
                                            first = false
                                        } else {
                                            path.lineTo(pt.x, pt.y)
                                        }
                                    }
                                }
                                path.close()

                                // Preenchimento do distrito
                                val fillColor = when {
                                    isSelected -> if (hasRain) intensity.color.copy(alpha = 0.88f) else Color(0x6638BDF8)
                                    hasRain -> intensity.color.copy(alpha = 0.68f)
                                    else -> Color(0x351E293B)
                                }
                                drawPath(path, fillColor, style = Fill)

                                // Borda do distrito (afina conforme o zoom aumenta)
                                val strokeColor = when {
                                    isSelected -> Color(0xFF38BDF8)
                                    hasRain -> intensity.color
                                    else -> Color(0xAA94A3B8)
                                }
                                val baseWidth = if (isSelected) 3.0f * density else 1.2f * density
                                val strokeWidth = (baseWidth / strokeScaleFactor).coerceAtLeast(0.4f * density)
                                drawPath(path, strokeColor, style = Stroke(width = strokeWidth))
                            }

                            // Ponto central e indicador para distrito selecionado (afinado no zoom)
                            if (isSelected) {
                                val centerPt = geoToOffset(dist.centroidLat, dist.centroidLon)
                                if (centerPt != null) {
                                    val dotRadius = (4.5f * density / markerScaleFactor).coerceAtLeast(1.5f * density)
                                    drawCircle(
                                        color = Color.White,
                                        radius = dotRadius,
                                        center = centerPt
                                    )
                                }
                            }
                        }

                        // 2. Anéis de Alcance (10km, 20km, 30km, 36km) centrados no radar USP
                        if (showRangeRings) {
                            val radarCenter = Offset(505f * scaleX, 495f * scaleY)
                            val ringDistancesKm = listOf(10f, 20f, 30f, 36f)
                            ringDistancesKm.forEach { dKm ->
                                val rPx = (dKm / RadarAnalysisEngine.KM_PER_PIXEL.toFloat()) * scaleX
                                val ringStrokeWidth = (1.2f * density / strokeScaleFactor).coerceAtLeast(0.4f * density)
                                drawCircle(
                                    color = Color(0x7738BDF8),
                                    radius = rPx,
                                    center = radarCenter,
                                    style = Stroke(width = ringStrokeWidth)
                                )
                            }
                        }

                        // 3. Pino de localização do usuário com efeito pulsante (afinado no zoom)
                        if (showUserPin && userAddress != null) {
                            val userPx = RadarAnalysisEngine.geoToPixel(userAddress.latitude, userAddress.longitude)
                            if (userPx != null) {
                                val userOffset = Offset(userPx.first * scaleX, userPx.second * scaleY)

                                // Onda de radar pulsante reduzida no zoom
                                val waveRadius = (pulseRadius * scaleX) / (1f + 0.35f * (zoomScale - 1f))
                                val waveStroke = (2.5f * density / strokeScaleFactor).coerceAtLeast(0.6f * density)
                                drawCircle(
                                    color = Color(0xFF38BDF8).copy(alpha = pulseAlpha),
                                    radius = waveRadius,
                                    center = userOffset,
                                    style = Stroke(width = waveStroke)
                                )

                                // Ponto central
                                val dotR1 = (7f / markerScaleFactor).coerceAtLeast(2.0f)
                                val dotR2 = (4f / markerScaleFactor).coerceAtLeast(1.2f)
                                drawCircle(
                                    color = Color(0xFF0284C7),
                                    radius = dotR1,
                                    center = userOffset
                                )
                                drawCircle(
                                    color = Color(0xFF38BDF8),
                                    radius = dotR2,
                                    center = userOffset
                                )

                                // Mira em cruz
                                val armLen = (14f / markerScaleFactor).coerceAtLeast(4.0f)
                                val armStroke = (1.8f * density / strokeScaleFactor).coerceAtLeast(0.5f * density)
                                drawLine(
                                    color = Color(0xFF38BDF8),
                                    start = Offset(userOffset.x - armLen, userOffset.y),
                                    end = Offset(userOffset.x + armLen, userOffset.y),
                                    strokeWidth = armStroke
                                )
                                drawLine(
                                    color = Color(0xFF38BDF8),
                                    start = Offset(userOffset.x, userOffset.y - armLen),
                                    end = Offset(userOffset.x, userOffset.y + armLen),
                                    strokeWidth = armStroke
                                )
                            }
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
                                zoomScale = (zoomScale + 0.5f).coerceAtMost(6.0f)
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

                // Legenda de modo no rodapé do mapa
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = when (forecastMode) {
                            1 -> "Modo: Previsão (+30m) • Toque para inspecionar"
                            2 -> "Modo: Previsão (+60m) • Toque para inspecionar"
                            else -> "Modo: Agora (Radar) • Toque para inspecionar"
                        },
                        fontSize = 10.sp,
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Detalhes do Distrito Selecionado ou Legenda
            if (selectedDistrict != null) {
                DistrictDetailBanner(
                    district = selectedDistrict,
                    forecastMode = forecastMode,
                    onSetAddress = { onSetAsMyAddress(selectedDistrict) },
                    onDismiss = { onSelectDistrict(null) }
                )
            } else {
                DistrictLegendRow()
            }
        }
    }
}

@Composable
private fun DistrictDetailBanner(
    district: DistrictRainStatus,
    forecastMode: Int,
    onSetAddress: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentRaining = district.rainPercent >= 8f && district.maxIntensity.isRaining
    val forecast30mRaining = district.forecast30mRainPercent >= 8f && district.forecast30mIntensity.isRaining
    val forecast60mRaining = district.forecast60mRainPercent >= 8f && district.forecast60mIntensity.isRaining

    val (activeIntensity, activeRaining, activeAlertStatus) = when (forecastMode) {
        1 -> Triple(district.forecast30mIntensity, forecast30mRaining, district.forecast30mAlertStatus)
        2 -> Triple(district.forecast60mIntensity, forecast60mRaining, district.forecast60mAlertStatus)
        else -> Triple(district.maxIntensity, currentRaining, district.cgeAlertStatus)
    }

    val activeColor = if (activeRaining) activeIntensity.color else Color(0xFF64748B)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF070F1E),
        border = androidx.compose.foundation.BorderStroke(1.dp, activeColor.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().testTag("district_detail_banner")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = district.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${district.zone} • Status CGE: $activeAlertStatus",
                        fontSize = 11.sp,
                        color = if (activeAlertStatus != "Normal") Color(0xFFEAB308) else Color(0xFF94A3B8)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = activeColor.copy(alpha = 0.22f)
                ) {
                    Text(
                        text = when (forecastMode) {
                            1 -> if (forecast30mRaining) district.forecast30mIntensity.label else "Seco em +30m"
                            2 -> if (forecast60mRaining) district.forecast60mIntensity.label else "Seco em +60m"
                            else -> if (currentRaining) district.maxIntensity.label else "Sem chuva"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeColor == Color(0xFF64748B)) Color.White else activeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Agora: ${if (currentRaining) "${district.maxIntensity.label} (${district.rainPercent.toInt()}%)" else "Sem chuva"}",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1)
                    )
                    Text(
                        text = "Em +30 min: ${if (forecast30mRaining) "${district.forecast30mIntensity.label} (${district.forecast30mRainPercent.toInt()}%)" else "Sem chuva prevista"}",
                        fontSize = 11.sp,
                        color = if (forecast30mRaining) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                    )
                    Text(
                        text = "Em +60 min: ${if (forecast60mRaining) "${district.forecast60mIntensity.label} (${district.forecast60mRainPercent.toInt()}%)" else "Sem chuva prevista"}",
                        fontSize = 11.sp,
                        color = if (forecast60mRaining) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                    )
                }

                Button(
                    onClick = onSetAddress,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.height(34.dp).testTag("btn_set_district_address")
                ) {
                    Icon(Icons.Default.LocationSearching, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Prever aqui", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DistrictLegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(color = Color(0xFF64748B), text = "Sem chuva")
        LegendDot(color = RainIntensity.LIGHT.color, text = "Leve")
        LegendDot(color = RainIntensity.MODERATE.color, text = "Moderada")
        LegendDot(color = RainIntensity.HEAVY.color, text = "Forte")
        LegendDot(color = RainIntensity.INTENSE.color, text = "Torrencial")
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}
