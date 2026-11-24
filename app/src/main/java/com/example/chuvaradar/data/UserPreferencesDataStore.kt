package com.example.chuvaradar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chuva_radar_prefs")

class UserPreferencesDataStore(private val context: Context) {

    companion object {
        private val KEY_ADDRESS_ID = stringPreferencesKey("address_id")
        private val KEY_ADDRESS_LABEL = stringPreferencesKey("address_label")
        private val KEY_FULL_ADDRESS = stringPreferencesKey("full_address")
        private val KEY_LATITUDE = doublePreferencesKey("latitude")
        private val KEY_LONGITUDE = doublePreferencesKey("longitude")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_BACKGROUND_SYNC_ENABLED = booleanPreferencesKey("background_sync_enabled")
        private val KEY_LAST_NOTIFICATION_MILLIS = longPreferencesKey("last_notification_millis")
        private val KEY_RECENT_ADDRESSES_JSON = stringPreferencesKey("recent_addresses_json")
    }

    val activeAddressFlow: Flow<UserSavedAddress> = context.dataStore.data.map { prefs ->
        val id = prefs[KEY_ADDRESS_ID] ?: "default_paulista"
        val label = prefs[KEY_ADDRESS_LABEL] ?: "Av. Paulista"
        val fullAddress = prefs[KEY_FULL_ADDRESS] ?: "Av. Paulista, 1000 - Bela Vista, São Paulo - SP"
        val lat = prefs[KEY_LATITUDE] ?: -23.5615
        val lon = prefs[KEY_LONGITUDE] ?: -46.6560
        UserSavedAddress(id, label, fullAddress, lat, lon, isSelected = true)
    }

    val recentAddressesFlow: Flow<List<UserSavedAddress>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[KEY_RECENT_ADDRESSES_JSON] ?: ""
        if (jsonStr.isBlank()) {
            emptyList()
        } else {
            try {
                val list = mutableListOf<UserSavedAddress>()
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        UserSavedAddress(
                            id = obj.optString("id", "addr_$i"),
                            label = obj.optString("label", ""),
                            fullAddress = obj.optString("fullAddress", ""),
                            latitude = obj.optDouble("latitude", 0.0),
                            longitude = obj.optDouble("longitude", 0.0),
                            isSelected = false
                        )
                    )
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    val backgroundSyncEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKGROUND_SYNC_ENABLED] ?: true
    }

    suspend fun getActiveAddress(): UserSavedAddress {
        return activeAddressFlow.first()
    }

    suspend fun saveActiveAddress(address: UserSavedAddress) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ADDRESS_ID] = address.id
            prefs[KEY_ADDRESS_LABEL] = address.label
            prefs[KEY_FULL_ADDRESS] = address.fullAddress
            prefs[KEY_LATITUDE] = address.latitude
            prefs[KEY_LONGITUDE] = address.longitude

            // Salva no histórico de recentes (mais recente para o mais antigo)
            val currentJson = prefs[KEY_RECENT_ADDRESSES_JSON] ?: ""
            val list = mutableListOf<UserSavedAddress>()

            // Adiciona o endereço atual na 1ª posição
            list.add(address)

            // Carrega os anteriores e remove duplicatas
            if (currentJson.isNotBlank()) {
                try {
                    val array = JSONArray(currentJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val prev = UserSavedAddress(
                            id = obj.optString("id", "addr_$i"),
                            label = obj.optString("label", ""),
                            fullAddress = obj.optString("fullAddress", ""),
                            latitude = obj.optDouble("latitude", 0.0),
                            longitude = obj.optDouble("longitude", 0.0)
                        )
                        // Evita duplicatas pelo label ou coordenadas muito próximas
                        val isDuplicate = prev.label.equals(address.label, ignoreCase = true) ||
                                (Math.abs(prev.latitude - address.latitude) < 0.002 &&
                                 Math.abs(prev.longitude - address.longitude) < 0.002)
                        if (!isDuplicate) {
                            list.add(prev)
                        }
                    }
                } catch (e: Exception) {
                    // ignora erros de parse
                }
            }

            // Limita aos 10 endereços mais recentes
            val trimmedList = list.take(10)
            val newArray = JSONArray()
            trimmedList.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("label", item.label)
                obj.put("fullAddress", item.fullAddress)
                obj.put("latitude", item.latitude)
                obj.put("longitude", item.longitude)
                newArray.put(obj)
            }
            prefs[KEY_RECENT_ADDRESSES_JSON] = newArray.toString()
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setBackgroundSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_SYNC_ENABLED] = enabled
        }
    }

    suspend fun getLastNotificationTime(): Long {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_LAST_NOTIFICATION_MILLIS] ?: 0L
    }

    suspend fun setLastNotificationTime(millis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_NOTIFICATION_MILLIS] = millis
        }
    }
}
