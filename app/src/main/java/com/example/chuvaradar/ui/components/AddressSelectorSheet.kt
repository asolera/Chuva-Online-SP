package com.example.chuvaradar.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.chuvaradar.data.DistrictData
import com.example.chuvaradar.data.UserSavedAddress
import com.google.android.gms.location.LocationServices

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressSelectorSheet(
    activeAddress: UserSavedAddress?,
    recentAddresses: List<UserSavedAddress>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSelectAddress: (UserSavedAddress) -> Unit,
    onSearchAddress: (String, (Boolean, String) -> Unit) -> Unit,
    onUseGpsLocation: (Double, Double, String) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    onUseGpsLocation(loc.latitude, loc.longitude, "Minha Localização GPS")
                    onDismiss()
                } else {
                    searchFeedbackMessage = "Não foi possível obter o sinal GPS no momento."
                }
            }
        } else {
            searchFeedbackMessage = "Permissão de localização negada."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F1E36)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configurar Endereço",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color(0xFF94A3B8))
                }
            }

            Text(
                text = "O nowcasting e o widget estimarão a chuva em relação ao local selecionado.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    searchFeedbackMessage = null
                },
                placeholder = { Text("Buscar endereço ou bairro em SP...", color = Color(0xFF64748B)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8))
                },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF38BDF8))
                    } else if (searchQuery.isNotBlank()) {
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                isSearching = true
                                onSearchAddress(searchQuery) { success, msg ->
                                    isSearching = false
                                    searchFeedbackMessage = msg
                                    if (success) onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp).padding(end = 4.dp).testTag("btn_search_address")
                        ) {
                            Text("Buscar", fontSize = 11.sp)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        if (searchQuery.isNotBlank()) {
                            isSearching = true
                            onSearchAddress(searchQuery) { success, msg ->
                                isSearching = false
                                searchFeedbackMessage = msg
                                if (success) onDismiss()
                            }
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF1E3A63),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF070F1E),
                    unfocusedContainerColor = Color(0xFF070F1E)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("address_search_field")
            )

            if (searchFeedbackMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = searchFeedbackMessage!!,
                    fontSize = 11.sp,
                    color = Color(0xFFF59E0B)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // GPS button
            Button(
                onClick = {
                    val permissionCheck = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                        @SuppressLint("MissingPermission")
                        fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                            if (loc != null) {
                                onUseGpsLocation(loc.latitude, loc.longitude, "Minha Localização GPS")
                                onDismiss()
                            } else {
                                searchFeedbackMessage = "Aguardando sinal GPS..."
                            }
                        }
                    } else {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp).testTag("btn_use_gps")
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Usar minha localização atual (GPS)", fontSize = 12.sp, color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // List of Addresses: Recents (newest to oldest) + Presets
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                // SEÇÃO 1: Últimos endereços acessados (ordenados do mais recente para o mais antigo)
                if (recentAddresses.isNotEmpty()) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Últimos Endereços Acessados (Recentes):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    items(recentAddresses) { recent ->
                        val isSelected = activeAddress?.label == recent.label
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF070F1E),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E3A63)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    onSelectAddress(recent)
                                    onDismiss()
                                }
                                .testTag("recent_address_${recent.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = recent.label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = recent.fullAddress,
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Ativo",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // SEÇÃO 2: Regiões Populares de SP (Presets)
                item {
                    Text(
                        text = "Regiões & Bairros Populares de SP:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                items(DistrictData.quickAddressPresets) { preset ->
                    val isSelected = activeAddress?.label == preset.label
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.2f) else Color(0xFF070F1E),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E3A63)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable {
                                onSelectAddress(preset)
                                onDismiss()
                            }
                            .testTag("preset_address_${preset.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = preset.fullAddress,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selecionado",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
