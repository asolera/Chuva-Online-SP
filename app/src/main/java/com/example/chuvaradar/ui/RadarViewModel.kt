package com.example.chuvaradar.ui

import android.app.Application
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.chuvaradar.background.RadarAlarmReceiver
import com.example.chuvaradar.background.RadarNotificationHelper
import com.example.chuvaradar.data.CantareiraRepository
import com.example.chuvaradar.data.CantareiraUiState
import com.example.chuvaradar.data.DistrictRainStatus
import com.example.chuvaradar.data.RadarRepository
import com.example.chuvaradar.data.RadarUiState
import com.example.chuvaradar.data.UserPreferencesDataStore
import com.example.chuvaradar.data.UserSavedAddress
import com.example.chuvaradar.widget.ChuvaRadarWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class RadarViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = UserPreferencesDataStore(application)
    private val repository = RadarRepository.getInstance(application)
    private val cantareiraRepository = CantareiraRepository.getInstance(application)

    val uiState: StateFlow<RadarUiState> = repository.uiState
    val cantareiraState: StateFlow<CantareiraUiState> = cantareiraRepository.uiState

    // Controles de animação do radar
    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackSpeedMs = MutableStateFlow(650L) // milissegundos por frame
    val playbackSpeedMs: StateFlow<Long> = _playbackSpeedMs.asStateFlow()

    // Camadas de sobreposição no radar
    private val _showUserPin = MutableStateFlow(true)
    val showUserPin: StateFlow<Boolean> = _showUserPin.asStateFlow()

    private val _showRangeRings = MutableStateFlow(true)
    val showRangeRings: StateFlow<Boolean> = _showRangeRings.asStateFlow()

    private val _showDistrictLabels = MutableStateFlow(true)
    val showDistrictLabels: StateFlow<Boolean> = _showDistrictLabels.asStateFlow()

    // Distrito selecionado para detalhes
    private val _selectedDistrict = MutableStateFlow<DistrictRainStatus?>(null)
    val selectedDistrict: StateFlow<DistrictRainStatus?> = _selectedDistrict.asStateFlow()

    // Notificações ativas (gerenciadas pelo ciclo de vida via stateIn)
    val notificationsEnabled: StateFlow<Boolean> = preferences.notificationsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // Histórico de endereços recentes (mais recente para o mais antigo via stateIn)
    val recentAddresses: StateFlow<List<UserSavedAddress>> = preferences.recentAddressesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var animationJob: Job? = null

    init {
        try {
            RadarNotificationHelper.createNotificationChannel(application)
        } catch (e: Exception) {
            Log.e("RadarViewModel", "Erro ao criar canal: ${e.message}")
        }

        try {
            RadarAlarmReceiver.scheduleAlarm(application)
        } catch (e: Exception) {
            Log.e("RadarViewModel", "Erro ao agendar alarme: ${e.message}")
        }

        // Carrega dados iniciais do radar e do Sistema Cantareira (SABESP)
        refreshRadar(showLoading = true)
        refreshCantareira(showLoading = true)

        // Inicia loop de reprodução automática
        startAnimationLoop()
    }

    fun refreshRadar(showLoading: Boolean = false) {
        viewModelScope.launch {
            val result = repository.refreshRadar(showLoadingIndicator = showLoading)
            val state = repository.uiState.value
            ChuvaRadarWidgetProvider.updateAllWidgets(
                getApplication(),
                result.getOrNull(),
                state.cityCoverage?.totalRainPercent
            )
        }
    }

    fun refreshCantareira(showLoading: Boolean = false) {
        viewModelScope.launch {
            cantareiraRepository.refresh(showLoadingIndicator = showLoading)
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            stopAnimationLoop()
        } else {
            startAnimationLoop()
        }
    }

    private fun startAnimationLoop() {
        _isPlaying.value = true
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            while (isActive) {
                delay(_playbackSpeedMs.value)
                val frames = uiState.value.frames
                if (frames.isNotEmpty()) {
                    val currentIndex = uiState.value.activeFrameIndex
                    val nextIndex = (currentIndex + 1) % frames.size
                    repository.setActiveFrame(nextIndex)
                }
            }
        }
    }

    fun stopAnimationLoop() {
        _isPlaying.value = false
        animationJob?.cancel()
        animationJob = null
    }

    fun stepForward() {
        stopAnimationLoop()
        val frames = uiState.value.frames
        if (frames.isNotEmpty()) {
            val nextIndex = (uiState.value.activeFrameIndex + 1) % frames.size
            repository.setActiveFrame(nextIndex)
        }
    }

    fun stepBackward() {
        stopAnimationLoop()
        val frames = uiState.value.frames
        if (frames.isNotEmpty()) {
            val prevIndex = (uiState.value.activeFrameIndex - 1 + frames.size) % frames.size
            repository.setActiveFrame(prevIndex)
        }
    }

    fun seekToFrame(index: Int) {
        repository.setActiveFrame(index)
    }

    fun toggleUserPin() {
        _showUserPin.value = !_showUserPin.value
    }

    fun toggleRangeRings() {
        _showRangeRings.value = !_showRangeRings.value
    }

    fun toggleDistrictLabels() {
        _showDistrictLabels.value = !_showDistrictLabels.value
    }

    fun selectDistrict(district: DistrictRainStatus?) {
        _selectedDistrict.value = district
    }

    fun selectPresetAddress(address: UserSavedAddress) {
        viewModelScope.launch {
            repository.updateActiveAddress(address)
            ChuvaRadarWidgetProvider.updateAllWidgets(
                getApplication(),
                uiState.value.nowcast,
                uiState.value.cityCoverage?.totalRainPercent
            )
        }
    }

    fun setLocationFromCoordinates(lat: Double, lon: Double, fallbackLabel: String) {
        viewModelScope.launch {
            val resolvedAddress = resolveAddressFromGeo(lat, lon, fallbackLabel)
            repository.updateActiveAddress(resolvedAddress)
            ChuvaRadarWidgetProvider.updateAllWidgets(
                getApplication(),
                uiState.value.nowcast,
                uiState.value.cityCoverage?.totalRainPercent
            )
        }
    }

    fun searchAddress(query: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val geocoder = Geocoder(getApplication(), Locale("pt", "BR"))
                val results: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var list: List<Address>? = null
                    geocoder.getFromLocationName("$query, São Paulo, SP, Brasil", 3) {
                        list = it
                    }
                    delay(800)
                    list
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName("$query, São Paulo, SP, Brasil", 3)
                }

                val found = results?.firstOrNull()
                if (found != null && found.hasLatitude() && found.hasLongitude()) {
                    val label = found.thoroughfare ?: found.subLocality ?: query
                    val full = found.getAddressLine(0) ?: "$query, São Paulo - SP"
                    val saved = UserSavedAddress(
                        id = "custom_${System.currentTimeMillis()}",
                        label = label,
                        fullAddress = full,
                        latitude = found.latitude,
                        longitude = found.longitude,
                        isSelected = true
                    )
                    selectPresetAddress(saved)
                    onResult(true, "Endereço localizado com sucesso!")
                } else {
                    onResult(false, "Não foi possível encontrar o endereço na Grande SP.")
                }
            } catch (e: Exception) {
                Log.e("RadarViewModel", "Erro no Geocoder: ${e.message}")
                onResult(false, "Erro ao buscar endereço: verifique a conexão.")
            }
        }
    }

    private fun resolveAddressFromGeo(lat: Double, lon: Double, fallbackLabel: String): UserSavedAddress {
        return try {
            val geocoder = Geocoder(getApplication(), Locale("pt", "BR"))
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lon, 1)
            val item = list?.firstOrNull()
            if (item != null) {
                val label = item.subLocality ?: item.thoroughfare ?: fallbackLabel
                val full = item.getAddressLine(0) ?: String.format(Locale.US, "Lat: %.4f, Lon: %.4f", lat, lon)
                UserSavedAddress("geo_${System.currentTimeMillis()}", label, full, lat, lon, isSelected = true)
            } else {
                UserSavedAddress("geo_${System.currentTimeMillis()}", fallbackLabel, String.format(Locale.US, "Lat: %.4f, Lon: %.4f", lat, lon), lat, lon, isSelected = true)
            }
        } catch (e: Exception) {
            UserSavedAddress("geo_${System.currentTimeMillis()}", fallbackLabel, String.format(Locale.US, "Lat: %.4f, Lon: %.4f", lat, lon), lat, lon, isSelected = true)
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNotificationsEnabled(enabled)
        }
    }

    fun triggerTestNotification() {
        val nowcast = uiState.value.nowcast
        if (nowcast != null) {
            RadarNotificationHelper.createNotificationChannel(getApplication())
            RadarNotificationHelper.showRainAlertNotification(getApplication(), nowcast)
        }
    }
}
