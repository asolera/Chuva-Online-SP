package com.example.chuvaradar.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.chuvaradar.data.RadarRepository
import com.example.chuvaradar.data.UserPreferencesDataStore
import com.example.chuvaradar.widget.ChuvaRadarWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RadarAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RadarAlarmReceiver"
        const val ACTION_TRIGGER_RADAR_CHECK = "com.example.chuvaradar.ACTION_TRIGGER_RADAR_CHECK"
        private const val INTERVAL_MILLIS = 5 * 60 * 1000L // 5 minutos
        private const val DEBOUNCE_NOTIFICATION_MILLIS = 20 * 60 * 1000L // 20 minutos entre notificações para o mesmo evento

        fun scheduleAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, RadarAlarmReceiver::class.java).apply {
                    action = ACTION_TRIGGER_RADAR_CHECK
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    100,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MILLIS

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerAt,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerAt,
                            pendingIntent
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Agendado próximo ciclo de checagem do radar em 5 minutos.")
            } catch (e: Exception) {
                Log.e(TAG, "Aviso ao agendar alarme de segundo plano: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarme recebido para checagem do radar IAG-USP (ação: ${intent.action})")

        // Reagenda o próximo ciclo de 5 minutos com proteção
        scheduleAlarm(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = UserPreferencesDataStore(context)
                val repository = RadarRepository.getInstance(context)

                // Executa atualização do radar
                val result = repository.refreshRadar()
                val nowcast = result.getOrNull()
                val state = repository.uiState.value

                // 1. Atualiza widgets da tela inicial
                ChuvaRadarWidgetProvider.updateAllWidgets(
                    context,
                    nowcast,
                    state.cityCoverage?.totalRainPercent
                )

                // 2. Avalia envio de notificação se configurado
                if (nowcast != null) {
                    val isDataOutdated = nowcast.isDataOutdated || state.isDataOutdated
                    if (isDataOutdated) {
                        Log.w(
                            TAG,
                            "Notificação de chuva BLOQUEADA: dados do radar desatualizados (${nowcast.dataAgeDescription}, última imagem: ${nowcast.fullScanDateTimeText}). Evitando alarmes falsos de chuva antiga."
                        )
                    } else {
                        val notifEnabled = prefs.notificationsEnabledFlow
                        val lastNotified = prefs.getLastNotificationTime()
                        val now = System.currentTimeMillis()

                        val isRainImminent = nowcast.isRainingNow || (nowcast.estimatedArrivalMinutes != null && nowcast.estimatedArrivalMinutes <= 40)
                        val isDebounced = (now - lastNotified) >= DEBOUNCE_NOTIFICATION_MILLIS

                        if (isRainImminent && isDebounced) {
                            RadarNotificationHelper.createNotificationChannel(context)
                            RadarNotificationHelper.showRainAlertNotification(context, nowcast)
                            prefs.setLastNotificationTime(now)
                            Log.i(TAG, "Notificação de alerta de chuva enviada para ${nowcast.addressLabel}!")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no ciclo de atualização em segundo plano: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
