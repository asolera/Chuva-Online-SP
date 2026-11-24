package com.example.chuvaradar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chuvaradar.data.DistrictData
import com.example.chuvaradar.data.UserSavedAddress
import com.example.chuvaradar.ui.components.AddressSelectorSheet
import com.example.chuvaradar.ui.components.CantareiraVolumeCard
import com.example.chuvaradar.ui.components.DistrictMinimap
import com.example.chuvaradar.ui.components.NowcastHeroCard
import com.example.chuvaradar.ui.components.RadarViewer
import com.example.chuvaradar.ui.components.RainTodayVerdictCard
import com.example.chuvaradar.ui.components.SettingsSheet
import com.example.chuvaradar.ui.components.WeatherForecastCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarMainScreen(
    viewModel: RadarViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val weatherForecastState by viewModel.weatherForecastState.collectAsState()
    val cantareiraState by viewModel.cantareiraState.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val showUserPin by viewModel.showUserPin.collectAsState()
    val showRangeRings by viewModel.showRangeRings.collectAsState()
    val showDistrictLabels by viewModel.showDistrictLabels.collectAsState()
    val selectedDistrict by viewModel.selectedDistrict.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val recentAddresses by viewModel.recentAddresses.collectAsState()

    var currentTab by remember { mutableIntStateOf(0) } // 0: Radar Vivo, 1: Distritos CGE

    var showAddressSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val addressSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070F1E))
            .windowInsetsPadding(WindowInsets.statusBars),
        containerColor = Color(0xFF070F1E),
        topBar = {
            RadarTopBar(
                isRefreshing = state.isRefreshing || weatherForecastState.isRefreshing || cantareiraState.isRefreshing,
                onRefresh = {
                    viewModel.refreshRadar()
                    viewModel.refreshWeatherForecast()
                    viewModel.refreshCantareira()
                },
                onOpenSettings = { showSettingsSheet = true }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Error banner if any
            if (state.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.errorMessage!!,
                            color = Color(0xFFFECACA),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Outdated Data Warning Banner
            if (state.isDataOutdated) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF451A03).copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .testTag("outdated_radar_warning_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Radar Desatualizado",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Imagens do Radar Desatualizadas",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFEF3C7)
                                )
                                Text(
                                    text = "Captura de ${state.nowcast?.fullScanDateTimeText ?: state.frames.lastOrNull()?.fullDateTimeText ?: "--"} (${state.dataAgeDescription})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFCD34D)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "O servidor do radar meteorológico IAG-USP está com problemas e fornecendo imagens antigas (atraso superior a 1 hora). Para evitar avisos incorretos de chuva que não está acontecendo, os alertas foram pausados automaticamente.",
                            color = Color(0xFFFDE68A),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // 1. HERO NOWCAST CARD (Estimativa se vai chover em breve)
            NowcastHeroCard(
                nowcast = state.nowcast,
                cityCoverage = state.cityCoverage,
                onAddressClick = { showAddressSheet = true },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 2. VIEW TABS: "Radar Vivo (USP)" vs "Distritos (CGE-SP)"
            SecondaryTabRow(
                selectedTabIndex = currentTab,
                containerColor = Color(0xFF0F1E36),
                contentColor = Color(0xFF38BDF8),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Radar Ao Vivo (36km)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("tab_radar_live")
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Distritos SP", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("tab_districts")
                )
            }

            // 3. TAB CONTENT
            if (currentTab == 0) {
                RadarViewer(
                    frames = state.frames,
                    activeFrameIndex = state.activeFrameIndex,
                    isPlaying = isPlaying,
                    userAddress = state.activeAddress,
                    showUserPin = showUserPin,
                    showRangeRings = showRangeRings,
                    showDistrictLabels = showDistrictLabels,
                    onPlayPauseToggle = { viewModel.togglePlayPause() },
                    onStepBackward = { viewModel.stepBackward() },
                    onStepForward = { viewModel.stepForward() },
                    onSeekFrame = { viewModel.seekToFrame(it) },
                    onTogglePin = { viewModel.toggleUserPin() },
                    onToggleRings = { viewModel.toggleRangeRings() },
                    onToggleDistricts = { viewModel.toggleDistrictLabels() }
                )
            } else {
                DistrictMinimap(
                    districts = state.districtStatuses,
                    selectedDistrict = selectedDistrict,
                    userAddress = state.activeAddress,
                    showUserPin = showUserPin,
                    showRangeRings = showRangeRings,
                    onTogglePin = { viewModel.toggleUserPin() },
                    onToggleRings = { viewModel.toggleRangeRings() },
                    onSelectDistrict = { viewModel.selectDistrict(it) },
                    onSetAsMyAddress = { dist ->
                        viewModel.setLocationFromCoordinates(
                            lat = dist.centroidLat,
                            lon = dist.centroidLon,
                            fallbackLabel = dist.name
                        )
                        viewModel.selectDistrict(null)
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. VAI CHOVER HOJE? (Veredito rápido Sim / Talvez / Não)
            RainTodayVerdictCard(
                state = weatherForecastState
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 5. PREVISÃO DO TEMPO (Gráfico de chuva horária + Próximos 8 dias - Open-Meteo)
            WeatherForecastCard(
                state = weatherForecastState,
                onRefresh = { viewModel.refreshWeatherForecast() }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 6. SISTEMA CANTAREIRA (Volume útil % e gráfico de 30 dias - SABESP)
            CantareiraVolumeCard(
                state = cantareiraState,
                onRefresh = { viewModel.refreshCantareira() }
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Modal Bottom Sheets
    if (showAddressSheet) {
        AddressSelectorSheet(
            activeAddress = state.activeAddress,
            recentAddresses = recentAddresses,
            sheetState = addressSheetState,
            onDismiss = { showAddressSheet = false },
            onSelectAddress = { address ->
                viewModel.selectPresetAddress(address)
                showAddressSheet = false
            },
            onSearchAddress = { query, callback ->
                viewModel.searchAddress(query, callback)
            },
            onUseGpsLocation = { lat, lon, label ->
                viewModel.setLocationFromCoordinates(lat, lon, label)
                showAddressSheet = false
            }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            sheetState = settingsSheetState,
            notificationsEnabled = notificationsEnabled,
            onToggleNotifications = { viewModel.toggleNotifications(it) },
            onTestNotification = { viewModel.triggerTestNotification() },
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun RadarTopBar(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF0F1E36),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Radar Icon",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Chuva Radar SP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Radar IAG-USP (Raio 36km)",
                    fontSize = 11.sp,
                    color = Color(0xFF38BDF8)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.testTag("btn_refresh_radar")
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF38BDF8)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Atualizar Radar",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("btn_open_settings")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configurações",
                    tint = Color(0xFF94A3B8)
                )
            }
        }
    }
}
